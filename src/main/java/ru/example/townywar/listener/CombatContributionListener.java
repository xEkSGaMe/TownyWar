package ru.example.townywar.listener;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.projectiles.ProjectileSource;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.CombatWindowManager;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarManager;
import ru.example.townywar.war.WarPhase;

/**
 * Учёт «вклада в бой»: золотые яблоки и эндер-перлы.
 * Считаются только во время боевого окна, в COMBAT-фазе и без перемирия,
 * в городах, участвующих в войне. Вне этих условий — молчим.
 */
public final class CombatContributionListener implements Listener {

    private final TownyWarPlugin plugin;
    private final WarManager warManager;
    private final CombatWindowManager windowManager;

    public CombatContributionListener(TownyWarPlugin plugin, WarManager warManager,
                                      CombatWindowManager windowManager) {
        this.plugin = plugin;
        this.warManager = warManager;
        this.windowManager = windowManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Material m = event.getItem().getType();
        if (m != Material.GOLDEN_APPLE && m != Material.ENCHANTED_GOLDEN_APPLE) return;

        Player player = event.getPlayer();
        War war = activeWarFor(player);
        if (war == null) return;

        plugin.getWarLogRepository().log(war.getId(), player.getName(), "GAPPLE_EATEN",
                "type=" + m.name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl)) return;
        ProjectileSource src = event.getEntity().getShooter();
        if (!(src instanceof Player player)) return;

        War war = activeWarFor(player);
        if (war == null) return;

        plugin.getWarLogRepository().log(war.getId(), player.getName(), "ENDER_PEARL_USED", null);
    }

    /**
     * Возвращает активную COMBAT-войну игрока при выполнении всех условий:
     * боевое окно открыто, фаза COMBAT, truce не активен.
     */
    private War activeWarFor(Player player) {
        if (!windowManager.isCombatWindowActive()) return null;
        Town town = plugin.getTownyHook().getTownOf(player);
        if (town == null) return null;
        War war = warManager.getWar(town.getName());
        if (war == null || !war.isActive() || war.getPhase() != WarPhase.COMBAT) return null;
        if (plugin.getWarshopManager() != null
                && plugin.getWarshopManager().isTruceActive(war.getId())) return null;
        return war;
    }
}