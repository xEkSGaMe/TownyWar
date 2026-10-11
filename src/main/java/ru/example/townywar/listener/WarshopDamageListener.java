package ru.example.townywar.listener;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.warshop.WarshopManager;

public final class WarshopDamageListener implements Listener {

    private final WarshopManager warshopManager;

    public WarshopDamageListener(TownyWarPlugin plugin, WarshopManager warshopManager) {
        this.warshopManager = warshopManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event);
        if (attacker == null) return;
        double mult = warshopManager.getDamageMultiplier(attacker);
        if (mult <= 1.0) return;
        event.setDamage(event.getDamage() * mult);
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
        }
        return null;
    }
}