package ru.example.townywar.listener;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarPhase;

/**
 * Показывает игроку сообщение при входе, если его город участвует
 * в фазе {@link WarPhase#PREPARATION}.
 */
public final class PlayerJoinListener implements Listener {

    private final TownyWarPlugin plugin;

    public PlayerJoinListener(TownyWarPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Town town = plugin.getTownyHook().getTownOf(player);
        if (town == null) return;

        War war = plugin.getWarManager().getWar(town.getName());
        if (war == null || war.getPhase() != WarPhase.PREPARATION) return;

        if (war.getAttackerTown().equalsIgnoreCase(town.getName())) {
            plugin.getMessages().send(player, "prep-attacker-join");
        } else if (war.getDefenderTown().equalsIgnoreCase(town.getName())) {
            plugin.getMessages().send(player, "prep-defender-join");
        }
    }
}