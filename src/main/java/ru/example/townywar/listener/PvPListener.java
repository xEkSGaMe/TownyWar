package ru.example.townywar.listener;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.CombatWindowManager;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarManager;
import ru.example.townywar.war.WarPhase;

public final class PvPListener implements Listener {

    private final TownyWarPlugin plugin;
    private final WarManager warManager;
    private final CombatWindowManager windowManager;

    public PvPListener(TownyWarPlugin plugin, WarManager warManager, CombatWindowManager windowManager) {
        this.plugin = plugin;
        this.warManager = warManager;
        this.windowManager = windowManager;
    }

    /**
     * Правила PvP во время боевого окна:
     * <ul>
     *     <li>Никто не воюет (townless/neutral) — не наш случай, решает Towny/ваниль.</li>
     *     <li>Ровно один воюет — блок (townless и нейтралы не лезут в войну,
     *         воюющие не бьют townless/нейтралов).</li>
     *     <li>Оба воюют, но в разных войнах — блок.</li>
     *     <li>Одна война, своя сторона по своим — блок.</li>
     *     <li>Одна война, но не COMBAT — блок.</li>
     *     <li>Одна война, COMBAT, truce активен — блок.</li>
     *     <li>Одна война, COMBAT, без truce, противники — разрешено.</li>
     * </ul>
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event);
        if (attacker == null) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        // Вне боевого окна мы вообще не диктуем правила PvP.
        // Towny + CombatWindowManager (форсированный PvP-off воюющих) сами разберутся.
        if (!windowManager.isCombatWindowActive()) return;

        Town victimTown = plugin.getTownyHook().getTownOf(victim);
        Town attackerTown = plugin.getTownyHook().getTownOf(attacker);

        War atkWar = attackerTown == null ? null : warManager.getWar(attackerTown.getName());
        War vicWar = victimTown == null ? null : warManager.getWar(victimTown.getName());
        boolean atkAtWar = atkWar != null && atkWar.isActive();
        boolean vicAtWar = vicWar != null && vicWar.isActive();

        // Никто не воюет — не наш случай, решает Towny/ваниль.
        // Покрывает: townless vs townless, townless vs neutral, neutral vs neutral.
        if (!atkAtWar && !vicAtWar) return;

        // Ровно один воюет, другой нет (townless или neutral) — блок.
        // Так нейтралы и игроки без города не лезут в войну,
        // и воюющие не бьют нейтралов/townless.
        if (atkAtWar != vicAtWar) {
            event.setCancelled(true);
            return;
        }

        // Оба воюющие, но в разных войнах — блок.
        if (atkWar.getId() != vicWar.getId()) {
            event.setCancelled(true);
            return;
        }

        // Одна война: своя сторона по своим не бьёт.
        if (attackerTown.getName().equalsIgnoreCase(victimTown.getName())) {
            event.setCancelled(true);
            return;
        }

        // Одна война, но не фаза боя — блок (PREPARATION и NEGOTIATION).
        if (atkWar.getPhase() != WarPhase.COMBAT) {
            event.setCancelled(true);
            return;
        }

        // Warshop: перемирие — блокируем PvP между сторонами.
        if (plugin.getWarshopManager() != null
                && plugin.getWarshopManager().isTruceActive(atkWar.getId())) {
            event.setCancelled(true);
            return;
        }

        // Противники одной войны, COMBAT, без перемирия — разрешено.
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Projectile proj) {
            ProjectileSource shooter = proj.getShooter();
            if (shooter instanceof Player p) return p;
        }
        return null;
    }
}