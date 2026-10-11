package ru.example.townywar.negotiation;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.DeleteTownEvent;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.economy.Account;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.assault.AssaultTntManager;
import ru.example.townywar.flag.FlagManager;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarManager;
import ru.example.townywar.war.WarOutcome;
import ru.example.townywar.war.WarPhase;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NegotiationManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** A9: сколько чанков переносим за один тик при аннексии. */
    private static final int ANNEXATION_BATCH_SIZE = 5;

    private final TownyWarPlugin plugin;
    private final WarManager warManager;
    private final TributeRepository tributeRepo;

    /**
     * A9: warId, для которых исход уже применяется. Не даёт повторно
     * применить другой исход, пока идёт батч-аннексия или пока идёт
     * окно от нажатия до endWar.
     */
    private final Set<Long> processing = ConcurrentHashMap.newKeySet();

    public NegotiationManager(TownyWarPlugin plugin, WarManager warManager, TributeRepository tributeRepo) {
        this.plugin = plugin; this.warManager = warManager; this.tributeRepo = tributeRepo;
    }

    public boolean isProcessing(long warId) { return processing.contains(warId); }

    public boolean canNegotiate(Player player, War war) {
        if (war == null || war.getPhase() != WarPhase.NEGOTIATION) return false;
        Town winner = plugin.getTownyHook().getTown(war.getWinnerTown());
        if (winner == null) return false;
        Town playerTown = plugin.getTownyHook().getTownOf(player);
        if (playerTown == null || !playerTown.getName().equalsIgnoreCase(winner.getName())) return false;
        return plugin.getTownyHook().isMayor(playerTown, player);
    }

    public boolean canApply(War war, WarOutcome outcome) {
        if (war == null || outcome == null) return false;
        if (outcome != WarOutcome.LOOT) return true;
        String winnerName = war.getWinnerTown();
        if (winnerName == null) return false;
        Town loser = plugin.getTownyHook().getTown(
                winnerName.equalsIgnoreCase(war.getAttackerTown()) ? war.getDefenderTown() : war.getAttackerTown());
        if (loser == null) return false;
        Account acc = loser.getAccount();
        return acc != null && acc.getHoldingBalance() > 0.0;
    }

    /**
     * A9: guard — не даём запустить applyOutcome повторно, пока
     * батч-аннексия или endWar ещё не завершились.
     */
    public void applyOutcome(War war, WarOutcome outcome, Player chooser) {
        if (war == null || outcome == null) return;
        if (war.getPhase() != WarPhase.NEGOTIATION) return;
        if (!processing.add(war.getId())) return;

        try {
            doApply(war, outcome, chooser);
        } catch (Throwable t) {
            processing.remove(war.getId());
            throw t;
        }
    }

    private void doApply(War war, WarOutcome outcome, Player chooser) {
        // A8: страховка — флаги этой войны должны быть удалены до любого
        // применения исхода. При штатном пути они уже удалены либо
        // FlagManager.tick(), либо через WarManager.endWar, но если
        // applyOutcome вызовут раньше, чем успеет tick, — подчищаем сами.
        FlagManager fm = plugin.getFlagManager();
        if (fm != null) fm.removeAllByWar(war.getId());

        // Assault TNT: та же страховка, что и для флагов — исход не должен
        // применяться, пока в мире висят TNT этой войны.
        AssaultTntManager atm = plugin.getAssaultTntManager();
        if (atm != null) atm.removeAllByWar(war.getId());

        String winnerName = war.getWinnerTown();
        if (winnerName == null) {
            plugin.getLogger().warning("applyOutcome: winnerTown == null для войны #" + war.getId());
            processing.remove(war.getId());
            return;
        }
        String loserName = winnerName.equalsIgnoreCase(war.getAttackerTown())
                ? war.getDefenderTown() : war.getAttackerTown();

        Town winner = plugin.getTownyHook().getTown(winnerName);
        Town loser = plugin.getTownyHook().getTown(loserName);
        if (winner == null || loser == null) {
            plugin.getLogger().warning("Исход не применён: город не найден (" + winnerName + "/" + loserName + ")");
            processing.remove(war.getId());
            return;
        }

        String actor = chooser != null ? chooser.getName() : "SYSTEM";

        switch (outcome) {
            case ANNEXATION -> applyAnnexation(war, winner, loser, chooser, actor, winnerName);
            case LOOT -> {
                applyLoot(war, winner, loser, actor);
                finishOutcome(war, winnerName, outcome, actor);
            }
            case TRIBUTE -> {
                applyTribute(war, winner, loser, actor);
                finishOutcome(war, winnerName, outcome, actor);
            }
        }
    }

    private void finishOutcome(War war, String winnerName, WarOutcome outcome, String actor) {
        processing.remove(war.getId());
        plugin.getWarLogRepository().log(war.getId(), actor, "OUTCOME_APPLIED", "outcome=" + outcome.name());
        warManager.endWar(war, winnerName, outcome.name());
    }

    /**
     * A9: аннексия разбита на батчи. За один тик переносим
     * {@link #ANNEXATION_BATCH_SIZE} чанков. При 100+ чанках это десятки
     * тиков вместо одного фриза. Каждый чанк проверяем перед переносом:
     * он всё ещё должен принадлежать проигравшему (админ мог что-то
     * сделать в процессе).
     */
    private void applyAnnexation(War war, Town winner, Town loser, Player chooser, String actor, String winnerName) {
        final String loserName = loser.getName();
        final List<TownBlock> blocks = new ArrayList<>(loser.getTownBlocks());
        final int total = blocks.size();

        if (total == 0) {
            completeAnnexation(war, winner, loser, chooser, actor, winnerName, 0, 0);
            return;
        }

        new BukkitRunnable() {
            int idx = 0;
            int moved = 0;
            int failed = 0;

            @Override
            public void run() {
                int batch = ANNEXATION_BATCH_SIZE;
                while (batch > 0 && idx < total) {
                    TownBlock tb = blocks.get(idx++);
                    batch--;

                    // Проверяем актуальность: блок всё ещё принадлежит loser'у.
                    try {
                        Town current = tb.getTownOrNull();
                        if (current == null || !current.getName().equalsIgnoreCase(loserName)) {
                            continue;
                        }
                    } catch (Throwable ignored) {
                        continue;
                    }

                    if (transferTownBlock(tb, loser, winner)) moved++;
                    else failed++;
                }

                if (idx >= total) {
                    cancel();
                    completeAnnexation(war, winner, loser, chooser, actor, winnerName, moved, failed);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void completeAnnexation(War war, Town winner, Town loser, Player chooser,
                                    String actor, String winnerName, int moved, int failed) {
        String loserName = loser.getName();

        plugin.getLogger().info("Аннексия " + loserName + " → " + winner.getName()
                + ": перенесено чанков " + moved + ", ошибок " + failed);
        plugin.getWarLogRepository().log(war.getId(), actor, "ANNEXATION_BLOCKS",
                "from=" + loserName + ";to=" + winner.getName() + ";moved=" + moved + ";failed=" + failed);

        List<Resident> residents = new ArrayList<>(loser.getResidents());
        for (Resident r : residents) {
            try { loser.removeResident(r); }
            catch (Throwable t) { plugin.getLogger().warning("Изгнание не удалось: " + t.getMessage()); }
        }

        try {
            TownyAPI.getInstance().getDataSource().removeTown(
                    loser, DeleteTownEvent.Cause.UNKNOWN, chooser, true);
        } catch (Throwable t) {
            plugin.getLogger().warning("Удаление города не удалось: " + t.getMessage());
        }

        if (plugin.getTownyHook().getTown(loserName) != null) {
            plugin.getLogger().warning("Город " + loserName + " всё ещё существует после removeTown!");
        }

        broadcastToTown(winner.getName(), "<gold>Победа! Город <yellow>" + loserName + "</yellow> аннексирован.</gold>");
        Bukkit.getServer().sendMessage(MM.deserialize("<red>Город <yellow>" + loserName
                + "</yellow> аннексирован городом <yellow>" + winner.getName() + "</yellow>.</red>"));

        finishOutcome(war, winnerName, WarOutcome.ANNEXATION, actor);
    }

    private boolean transferTownBlock(TownBlock tb, Town from, Town to) {
        if (tb == null || to == null) return false;
        try {
            if (from != null && from.hasTownBlock(tb)) {
                from.removeTownBlock(tb);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[transfer] removeTownBlock fail: " + t.getMessage());
        }
        try {
            tb.setTown(to);
        } catch (Throwable t) {
            plugin.getLogger().warning("[transfer] setTown fail: " + t.getMessage());
            return false;
        }
        try {
            if (!to.hasTownBlock(tb)) {
                to.addTownBlock(tb);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[transfer] addTownBlock fail: " + t.getMessage());
        }
        try {
            TownyAPI.getInstance().getDataSource().saveTownBlock(tb);
        } catch (Throwable t) {
            plugin.getLogger().warning("[transfer] saveTownBlock fail: " + t.getMessage());
        }
        try {
            if (from != null) TownyAPI.getInstance().getDataSource().saveTown(from);
        } catch (Throwable ignored) { }
        try {
            TownyAPI.getInstance().getDataSource().saveTown(to);
        } catch (Throwable ignored) { }
        return true;
    }

    /**
     * A7: роллбэк при неудачном deposit.
     * Сначала withdraw, потом deposit; если deposit упал — возвращаем
     * деньги обратно лузеру. Если и роллбэк не удался — пишем SEVERE
     * с точной суммой, чтобы администратор мог вернуть вручную.
     */
    private void applyLoot(War war, Town winner, Town loser, String actor) {
        Account wa = winner.getAccount(), la = loser.getAccount();
        if (wa == null || la == null) return;

        double bal = la.getHoldingBalance();
        if (bal <= 0) return;

        if (!la.withdraw(bal, "War loot")) return;

        if (!wa.deposit(bal, "War loot")) {
            plugin.getLogger().warning("LOOT deposit failed, откат: " + bal
                    + " от " + loser.getName() + " к " + winner.getName());
            if (!la.deposit(bal, "War loot refund")) {
                plugin.getLogger().severe("LOOT rollback failed! Потеряно " + bal
                        + " между " + loser.getName() + " и " + winner.getName()
                        + " (война #" + war.getId() + ")");
            }
            return;
        }

        plugin.getWarLogRepository().log(war.getId(), actor, "LOOT_APPLIED",
                "amount=" + bal + ";from=" + loser.getName() + ";to=" + winner.getName());
        broadcastToTown(winner.getName(), "<gold>Вы разграбили казну <yellow>" + loser.getName()
                + "</yellow> на <yellow>" + String.format("%.0f", bal) + "</yellow> монет.</gold>");
        broadcastToTown(loser.getName(), "<red>Ваша казна разграблена: <yellow>-" + String.format("%.0f", bal) + "</yellow>.</red>");
    }

    private void applyTribute(War war, Town winner, Town loser, String actor) {
        long nextAt = nextTributeTime();
        // C11: запись синхронная — трибут должен быть в БД ДО endWar,
        // иначе при жёстком выключении между insert (async) и endWar
        // война останется ENDED, а трибута в БД не будет.
        tributeRepo.insertSync(war.getId(), loser.getName(), winner.getName(),
                plugin.getConfigManager().getTributePercent(), nextAt);
        plugin.getWarLogRepository().log(war.getId(), actor, "TRIBUTE_APPLIED",
                "payer=" + loser.getName() + ";receiver=" + winner.getName()
                        + ";percent=" + plugin.getConfigManager().getTributePercent());
        broadcastToTown(winner.getName(), "<gold>Город <yellow>" + loser.getName()
                + "</yellow> будет платить <yellow>" + plugin.getConfigManager().getTributePercent() + "%</yellow> ежедневно.</gold>");
        broadcastToTown(loser.getName(), "<red>На вас наложена контрибуция <yellow>"
                + plugin.getConfigManager().getTributePercent() + "%</yellow> в пользу <yellow>" + winner.getName() + "</yellow>.</red>");
    }

    private long nextTributeTime() {
        var zone = plugin.getConfigManager().getZoneId();
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime target = now.withHour(plugin.getConfigManager().getTributeHour())
                .withMinute(plugin.getConfigManager().getTributeMinute())
                .withSecond(0).withNano(0);
        if (!target.isAfter(now)) target = target.plusDays(1);
        return target.toInstant().toEpochMilli();
    }

    private void broadcastToTown(String townName, String miniMessage) {
        Town t = plugin.getTownyHook().getTown(townName);
        if (t == null) return;
        var msg = MM.deserialize(miniMessage);
        for (Resident r : t.getResidents()) { Player p = r.getPlayer(); if (p != null) p.sendMessage(msg); }
    }
}