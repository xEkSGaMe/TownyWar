package ru.example.townywar.command;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.negotiation.NegotiationMenu;
import ru.example.townywar.war.WarManager;
import ru.example.townywar.warshop.WarshopCommand;

import java.util.Arrays;
import java.util.Locale;

public final class TownyCommandInterceptor implements Listener {

    private final WarCommand warCommand;
    private final WarListCommand warListCommand;
    private final NegotiationCommand negotiationCommand;
    private final WarshopCommand warshopCommand;

    public TownyCommandInterceptor(TownyWarPlugin plugin, WarManager warManager,
                                   NegotiationMenu negotiationMenu, WarListCommand warListCommand,
                                   WarshopCommand warshopCommand) {
        this.warCommand = new WarCommand(plugin, warManager);
        this.warListCommand = warListCommand;
        this.negotiationCommand = new NegotiationCommand(plugin, negotiationMenu);
        this.warshopCommand = warshopCommand;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage();
        if (msg.isEmpty() || msg.charAt(0) != '/') return;
        String[] parts = msg.substring(1).split("\\s+");
        if (parts.length < 2) return;
        String root = parts[0].toLowerCase(Locale.ROOT);
        if (!(root.equals("t") || root.equals("town") || root.equals("towny"))) return;
        String sub = parts[1].toLowerCase(Locale.ROOT);
        Player p = e.getPlayer();
        switch (sub) {
            case "war" -> {
                e.setCancelled(true);
                String[] args = Arrays.copyOfRange(parts, 2, parts.length);
                warCommand.execute(p, args);
            }
            case "warlist" -> {
                e.setCancelled(true);
                warListCommand.execute(p);
            }
            case "negotiation" -> {
                e.setCancelled(true);
                negotiationCommand.execute(p);
            }
            case "warshop" -> {
                e.setCancelled(true);
                warshopCommand.execute(p);
            }
            default -> { }
        }
    }
}