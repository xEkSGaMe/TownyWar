package ru.example.townywar.listener;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.CombatWindowManager;

/**
 * Запрещает ломать стойки для брони в enemy-чанках во время боя.
 */
public final class ArmorStandListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CombatWindowManager windowManager;

    public ArmorStandListener(TownyWarPlugin plugin, CombatWindowManager windowManager) {
        this.windowManager = windowManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand)) return;
        if (!windowManager.isCombatWindowActive()) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (!windowManager.isEnemyChunkFor(player, event.getEntity().getLocation())) return;

        event.setCancelled(true);
        player.sendActionBar(MM.deserialize(
                "<red>Стойки для брони нельзя ломать в чанках противника.</red>"));
    }
}