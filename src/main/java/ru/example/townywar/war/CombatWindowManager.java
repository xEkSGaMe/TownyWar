package ru.example.townywar.war;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.config.ConfigManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CombatWindowManager {

    public interface WindowHook {
        void onWindowOpen();
        void onWindowClose();
    }

    private final TownyWarPlugin plugin;
    private final ConfigManager config;
    private final WarManager warManager;

    // Города (lowercase), которым МЫ принудительно включили PvP/взрывы
    // и ещё не выключили обратно.
    private final Set<String> enforcedPvp = ConcurrentHashMap.newKeySet();
    private final Set<String> enforcedExplosion = ConcurrentHashMap.newKeySet();
    private final List<WindowHook> hooks = new CopyOnWriteArrayList<>();

    private boolean lastState;
    private BukkitTask ticker;

    public CombatWindowManager(TownyWarPlugin plugin, ConfigManager config, WarManager warManager) {
        this.plugin = plugin; this.config = config; this.warManager = warManager;
    }

    public void start() {
        this.lastState = warManager.isCombatWindowActive();

        // При старте — форсим корректное состояние у всех активных COMBAT-войн.
        // Если окно закрыто — выключаем PvP/взрывы, даже если нас об этом
        // не просили (защита от «залипшего» состояния после рестарта/краша).
        if (!lastState) {
            for (War w : warManager.getActiveWars()) {
                if (w.getPhase() != WarPhase.COMBAT) continue;
                String a = w.getAttackerTown().toLowerCase();
                String d = w.getDefenderTown().toLowerCase();
                setPvpForTown(a, false);
                setPvpForTown(d, false);
                setExplosionForTown(a, false);
                setExplosionForTown(d, false);
            }
        }

        syncTowns();
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        for (String t : new ArrayList<>(enforcedPvp)) {
            enforcedPvp.remove(t);
            setPvpForTown(t, false);
        }
        for (String t : new ArrayList<>(enforcedExplosion)) {
            enforcedExplosion.remove(t);
            setExplosionForTown(t, false);
        }
    }

    public void registerHook(WindowHook hook) { hooks.add(hook); }

    public boolean isCombatWindowActive() { return warManager.isCombatWindowActive(); }

    private void tick() {
        boolean now = warManager.isCombatWindowActive();
        if (now != lastState) {
            lastState = now;
            if (now) {
                for (WindowHook h : hooks) safeRun(h::onWindowOpen);
                plugin.getWarLogRepository().log(null, "SYSTEM", "WINDOW_OPEN", null);
            } else {
                for (WindowHook h : hooks) safeRun(h::onWindowClose);
                plugin.getWarLogRepository().log(null, "SYSTEM", "WINDOW_CLOSE", null);
            }
        }
        syncTowns();
    }

    private void syncTowns() {
        boolean windowActive = warManager.isCombatWindowActive();

        Set<String> needOn = new HashSet<>();
        if (windowActive) {
            for (War w : warManager.getActiveWars()) {
                if (w.getPhase() != WarPhase.COMBAT) continue;
                needOn.add(w.getAttackerTown().toLowerCase());
                needOn.add(w.getDefenderTown().toLowerCase());
            }
        }

        for (String townKey : needOn) {
            if (enforcedPvp.add(townKey)) setPvpForTown(townKey, true);
            if (enforcedExplosion.add(townKey)) setExplosionForTown(townKey, true);
        }

        for (String townKey : new ArrayList<>(enforcedPvp)) {
            if (needOn.contains(townKey)) continue;
            enforcedPvp.remove(townKey);
            setPvpForTown(townKey, false);
        }
        for (String townKey : new ArrayList<>(enforcedExplosion)) {
            if (needOn.contains(townKey)) continue;
            enforcedExplosion.remove(townKey);
            setExplosionForTown(townKey, false);
        }
    }

    private static void safeRun(Runnable r) { try { r.run(); } catch (Throwable t) { t.printStackTrace(); } }

    private void setPvpForTown(String name, boolean pvp) {
        Town t = plugin.getTownyHook().getTown(name);
        if (t == null) {
            plugin.getLogger().warning("[CombatWindow] Town not found: " + name);
            return;
        }
        try {
            if (t.isPVP() == pvp) return;
            t.setPVP(pvp);
            plugin.getLogger().info("[CombatWindow] setPVP(" + t.getName() + ", " + pvp + ")");
        } catch (Throwable ex) {
            plugin.getLogger().warning("[CombatWindow] setPVP fail " + name + ": " + ex.getMessage());
        }
    }

    private void setExplosionForTown(String name, boolean explosion) {
        Town t = plugin.getTownyHook().getTown(name);
        if (t == null) return;
        try {
            if (t.isExplosion() == explosion) return;
            t.setExplosion(explosion);
            plugin.getLogger().info("[CombatWindow] setExplosion(" + t.getName() + ", " + explosion + ")");
        } catch (Throwable ex) {
            plugin.getLogger().warning("[CombatWindow] setExplosion fail " + name + ": " + ex.getMessage());
        }
    }

    public boolean isEnemyChunkFor(Player player, Location loc) {
        var towny = plugin.getTownyHook().api();
        if (loc.getWorld() == null || !towny.isTownyWorld(loc.getWorld())) return false;
        var tb = towny.getTownBlock(loc);
        if (tb == null || !tb.hasTown()) return false;
        Town chunkTown = tb.getTownOrNull();
        if (chunkTown == null) return false;
        Town playerTown = plugin.getTownyHook().getTownOf(player);
        if (playerTown == null) return false;
        War chunkWar = warManager.getWar(chunkTown.getName());
        if (chunkWar == null || chunkWar.getPhase() != WarPhase.COMBAT) return false;
        if (!chunkWar.involves(playerTown.getName())) return false;
        if (playerTown.getName().equalsIgnoreCase(chunkTown.getName())) return false;
        // Перемирие: вражеский чанк снова под защитой Towny —
        // блоки не ломаются/не ставятся, рамки/стойки неприкосновенны.
        if (plugin.getWarshopManager() != null
                && plugin.getWarshopManager().isTruceActive(chunkWar.getId())) return false;
        return true;
    }
}