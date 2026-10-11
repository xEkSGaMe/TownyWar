package ru.example.townywar.listener;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.CombatWindowManager;

/**
 * Запрещает ломать/ставить рамки и картины в enemy-чанках во время боя.
 */
public final class HangingListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CombatWindowManager windowManager;

    public HangingListener(TownyWarPlugin plugin, CombatWindowManager windowManager) {
        this.windowManager = windowManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(HangingBreakByEntityEvent event) {
        if (!windowManager.isCombatWindowActive()) return;
        if (!(event.getRemover() instanceof Player player)) return;
        if (!windowManager.isEnemyChunkFor(player, event.getEntity().getLocation())) return;

        event.setCancelled(true);
        player.sendActionBar(MM.deserialize(
                "<red>Рамки и картины нельзя ломать в чанках противника.</red>"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(HangingPlaceEvent event) {
        if (!windowManager.isCombatWindowActive()) return;
        Player player = event.getPlayer();
        if (player == null) return;
        if (!windowManager.isEnemyChunkFor(player, event.getEntity().getLocation())) return;

        event.setCancelled(true);
        player.sendActionBar(MM.deserialize(
                "<red>Рамки и картины нельзя ставить в чанках противника.</red>"));
    }
}