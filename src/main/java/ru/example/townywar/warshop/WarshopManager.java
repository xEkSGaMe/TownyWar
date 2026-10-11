package ru.example.townywar.warshop;

import com.palmergames.bukkit.towny.object.Town;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarPhase;
import ru.example.townywar.war.WarSide;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WarshopManager {

    public enum Result { SUCCESS, TOWN_REQUIRED, NOT_IN_COMBAT, ON_COOLDOWN, NOT_ENOUGH_MONEY,
        ECONOMY_UNAVAILABLE, TRUCE_ALREADY_ACTIVE, WITHDRAW_FAILED, INTERNAL_ERROR }

    public record Outcome(Result result, long cooldownRemainingMs) {
        static Outcome ok() { return new Outcome(Result.SUCCESS, 0); }
        static Outcome fail(Result r) { return new Outcome(r, 0); }
        static Outcome cd(long ms) { return new Outcome(Result.ON_COOLDOWN, ms); }
    }

    private final TownyWarPlugin plugin;
    private final WarshopRepository repo;

    // town(lower) -> ready/until epoch ms
    private final Map<String, Long> inspirationReady = new ConcurrentHashMap<>();
    private final Map<String, Long> truceReady = new ConcurrentHashMap<>();
    private final Map<String, Long> mercenariesReady = new ConcurrentHashMap<>();
    private final Map<String, Long> inspirationUntil = new ConcurrentHashMap<>();

    // warId -> truceUntil epoch ms
    private final Map<Long, Long> truceUntil = new ConcurrentHashMap<>();

    public WarshopManager(TownyWarPlugin plugin, WarshopRepository repo) {
        this.plugin = plugin; this.repo = repo;
    }

    public void load() {
        inspirationReady.clear(); truceReady.clear(); mercenariesReady.clear(); inspirationUntil.clear();
        for (var e : repo.loadAll().entrySet()) {
            String k = e.getKey();
            var s = e.getValue();
            if (s.inspirationReadyAt() > 0) inspirationReady.put(k, s.inspirationReadyAt());
            if (s.truceReadyAt() > 0) truceReady.put(k, s.truceReadyAt());
            if (s.mercenariesReadyAt() > 0) mercenariesReady.put(k, s.mercenariesReadyAt());
            if (s.inspirationUntil() > 0) inspirationUntil.put(k, s.inspirationUntil());
        }
        truceUntil.clear();
        truceUntil.putAll(repo.loadTruces());
    }

    public void shutdown() {
        repo.flushAndShutdown(this::persistAllSync);
    }

    private void persistAllSync() {
        // best-effort, sync — если БД уже закрыта, ловим и молча выходим
        try {
            for (String town : unionKeys()) {
                repo.upsertTownState(town, snapshotFor(town));
            }
            for (var e : truceUntil.entrySet()) {
                repo.upsertTruce(e.getKey(), e.getValue());
            }
        } catch (Throwable ignored) { }
    }

    private java.util.Set<String> unionKeys() {
        var s = new java.util.HashSet<String>();
        s.addAll(inspirationReady.keySet());
        s.addAll(truceReady.keySet());
        s.addAll(mercenariesReady.keySet());
        s.addAll(inspirationUntil.keySet());
        return s;
    }

    private WarshopRepository.TownState snapshotFor(String town) {
        return new WarshopRepository.TownState(
                inspirationReady.getOrDefault(town, 0L),
                truceReady.getOrDefault(town, 0L),
                mercenariesReady.getOrDefault(town, 0L),
                inspirationUntil.getOrDefault(town, 0L));
    }

    private void persistTown(String town) { repo.upsertTownState(town, snapshotFor(town)); }

    // --- queries ---

    public boolean isInspirationActive(String town) {
        Long until = inspirationUntil.get(town.toLowerCase());
        return until != null && until > System.currentTimeMillis();
    }

    public boolean isTruceActive(long warId) {
        Long until = truceUntil.get(warId);
        return until != null && until > System.currentTimeMillis();
    }

    public long truceRemainingMs(long warId) {
        Long until = truceUntil.get(warId);
        if (until == null) return 0;
        return Math.max(0, until - System.currentTimeMillis());
    }

    public long cooldownRemaining(WarshopItem item, String town) {
        String k = town.toLowerCase();
        long now = System.currentTimeMillis();
        long ready = switch (item) {
            case INSPIRATION -> inspirationReady.getOrDefault(k, 0L);
            case TRUCE -> truceReady.getOrDefault(k, 0L);
            case MERCENARIES -> mercenariesReady.getOrDefault(k, 0L);
        };
        return Math.max(0, ready - now);
    }

    public double getDamageMultiplier(Player attacker) {
        Town town = plugin.getTownyHook().getTownOf(attacker);
        if (town == null) return 1.0;
        if (!isInspirationActive(town.getName())) return 1.0;
        return 1.0 + plugin.getConfigManager().getInspirationBonusPercent() / 100.0;
    }

    /** Требуется вызывать из тикера раз в 20 тиков — очистка просроченных состояний. */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        // Inspiration until
        inspirationUntil.entrySet().removeIf(e -> e.getValue() <= now);
        // Truce: если истёк — удалить из БД
        var expiredWars = new java.util.ArrayList<Long>();
        for (var e : truceUntil.entrySet()) {
            if (e.getValue() <= now) expiredWars.add(e.getKey());
        }
        for (Long id : expiredWars) {
            truceUntil.remove(id);
            repo.deleteTruce(id);
        }
    }

    // --- buy ---

    public Outcome tryBuy(Player buyer, WarshopItem item) {
        Town town = plugin.getTownyHook().getTownOf(buyer);
        if (town == null) return Outcome.fail(Result.TOWN_REQUIRED);

        War war = plugin.getWarManager().getWar(town.getName());
        if (war == null || war.getPhase() != WarPhase.COMBAT) return Outcome.fail(Result.NOT_IN_COMBAT);

        long remaining = cooldownRemaining(item, town.getName());
        if (remaining > 0) return Outcome.cd(remaining);

        if (item == WarshopItem.TRUCE && isTruceActive(war.getId())) {
            return Outcome.fail(Result.TRUCE_ALREADY_ACTIVE);
        }

        int cost = costOf(item);
        Economy eco = plugin.getEconomyHook().economy();
        if (eco == null) return Outcome.fail(Result.ECONOMY_UNAVAILABLE);
        if (eco.getBalance(buyer) < cost) return Outcome.fail(Result.NOT_ENOUGH_MONEY);

        if (!eco.withdrawPlayer(buyer, cost).transactionSuccess()) return Outcome.fail(Result.WITHDRAW_FAILED);

        long now = System.currentTimeMillis();
        try {
            switch (item) {
                case INSPIRATION -> {
                    long dur = plugin.getConfigManager().getInspirationDurationMin() * 60_000L;
                    inspirationUntil.put(town.getName().toLowerCase(), now + dur);
                    inspirationReady.put(town.getName().toLowerCase(),
                            now + plugin.getConfigManager().getInspirationCooldownMin() * 60_000L);
                }
                case TRUCE -> {
                    long dur = plugin.getConfigManager().getTruceDurationMin() * 60_000L;
                    truceUntil.put(war.getId(), now + dur);
                    truceReady.put(town.getName().toLowerCase(),
                            now + plugin.getConfigManager().getTruceCooldownMin() * 60_000L);
                    repo.upsertTruce(war.getId(), now + dur);
                    Bukkit.getServer().sendMessage(plugin.getMessages().format("warshop-truce-start",
                            "attacker", war.getAttackerTown(), "defender", war.getDefenderTown(),
                            "minutes", String.valueOf(plugin.getConfigManager().getTruceDurationMin())));
                }
                case MERCENARIES -> {
                    WarSide side = war.sideOf(town.getName());
                    if (side != null) {
                        plugin.getWarManager().changePoints(war, side,
                                plugin.getConfigManager().getMercenariesPoints());
                    }
                    mercenariesReady.put(town.getName().toLowerCase(),
                            now + plugin.getConfigManager().getMercenariesCooldownMin() * 60_000L);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().severe("Warshop apply failed: " + t.getMessage());
            // вернуть деньги, если применение упало
            eco.depositPlayer(buyer, cost);
            return Outcome.fail(Result.INTERNAL_ERROR);
        }

        persistTown(town.getName());
        plugin.getWarLogRepository().log(war.getId(), buyer.getName(), "WARSHOP_BUY",
                "item=" + item.key() + ";town=" + town.getName() + ";cost=" + cost);
        return Outcome.ok();
    }

    public int costOf(WarshopItem item) {
        var cfg = plugin.getConfigManager();
        return switch (item) {
            case INSPIRATION -> cfg.getInspirationCost();
            case TRUCE -> cfg.getTruceCost();
            case MERCENARIES -> cfg.getMercenariesCost();
        };
    }
}