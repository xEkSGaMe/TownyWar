package ru.example.townywar.listener;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitTask;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.CombatWindowManager;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarManager;
import ru.example.townywar.war.WarPhase;
import ru.example.townywar.war.WarSide;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обработчик смертей во время войны.
 * <p>
 * Считаются <b>только PvP-убийства</b> — то есть случаи, когда
 * {@link Player#getKiller()} возвращает игрока-убийцу.
 * <p>
 * Warshop: пока активно Перемирие — очки не двигаются вообще,
 * независимо от типа смерти.
 */
public final class PvpDeathListener implements Listener {

    private static final long DOUBLE_FIRE_GUARD_MS = 2_000L;
    private static final long CLEANUP_PERIOD_TICKS = 600L;

    private final TownyWarPlugin plugin;
    private final WarManager warManager;
    private final CombatWindowManager windowManager;
    private final Map<UUID, Long> recentDeaths = new ConcurrentHashMap<>();

    /** D4: handle периодической чистки, чтобы можно было отменить при выключении. */
    private final BukkitTask cleanupTask;

    public PvpDeathListener(TownyWarPlugin plugin, WarManager warManager, CombatWindowManager windowManager) {
        this.plugin = plugin; this.warManager = warManager; this.windowManager = windowManager;

        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long cutoff = System.currentTimeMillis() - DOUBLE_FIRE_GUARD_MS;
            recentDeaths.entrySet().removeIf(e -> e.getValue() < cutoff);
        }, CLEANUP_PERIOD_TICKS, CLEANUP_PERIOD_TICKS);
    }

    public void stop() {
        if (cleanupTask != null) cleanupTask.cancel();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDeath(PlayerDeathEvent event) {
        // Очки двигаются только в боевое окно.
        if (!windowManager.isCombatWindowActive()) return;

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;

        UUID vid = victim.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = recentDeaths.get(vid);
        if (last != null && now - last < DOUBLE_FIRE_GUARD_MS) return;
        recentDeaths.put(vid, now);

        Town victimTown = plugin.getTownyHook().getTownOf(victim);
        Town killerTown = plugin.getTownyHook().getTownOf(killer);

        if (victimTown == null || killerTown == null) return;
        if (victimTown.getName().equalsIgnoreCase(killerTown.getName())) return;

        War war = warManager.getWar(victimTown.getName());
        if (war == null || war.getPhase() != WarPhase.COMBAT) return;

        // Warshop: перемирие — очки не двигаются.
        if (plugin.getWarshopManager() != null
                && plugin.getWarshopManager().isTruceActive(war.getId())) return;

        if (!war.involves(killerTown.getName())) return;

        WarSide victimSide = war.sideOf(victimTown.getName());
        if (victimSide == null) return;

        boolean isMayor = plugin.getTownyHook().isMayor(victimTown, victim);
        int delta = isMayor
                ? plugin.getConfigManager().getDeathMayor()
                : plugin.getConfigManager().getDeathResident();

        warManager.changePoints(war, victimSide, -delta);
        plugin.getWarLogRepository().log(war.getId(), killer.getName(), "PVP_KILL",
                "victim=" + victim.getName() + ";victimTown=" + victimTown.getName()
                        + ";mayor=" + isMayor + ";delta=" + delta);
        // Зеркальная запись: в статистику смерти идёт так же, как и убийство,
        // чтобы вклад каждой стороны считался симметрично.
        plugin.getWarLogRepository().log(war.getId(), victim.getName(), "PVP_DEATH",
                "killer=" + killer.getName() + ";victimTown=" + victimTown.getName()
                        + ";mayor=" + isMayor + ";delta=" + delta);

        if (war.getAttackerPoints() <= 0 || war.getDefenderPoints() <= 0) warManager.startNegotiation(war);
    }
}