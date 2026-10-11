package ru.example.townywar.warshop;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarPhase;

public final class WarshopCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final TownyWarPlugin plugin;
    private final WarshopMenu menu;

    public WarshopCommand(TownyWarPlugin plugin, WarshopMenu menu) {
        this.plugin = plugin; this.menu = menu;
    }

    public void execute(Player player) {
        if (!player.hasPermission("townywar.warshop")) {
            plugin.getMessages().send(player, "no-permission");
            return;
        }
        var town = plugin.getTownyHook().getTownOf(player);
        if (town == null) {
            plugin.getMessages().send(player, "not-in-town");
            return;
        }
        War war = plugin.getWarManager().getWar(town.getName());
        if (war == null || war.getPhase() != WarPhase.COMBAT) {
            player.sendActionBar(MM.deserialize("<red>Магазин доступен только во время боевых действий.</red>"));
            return;
        }
        menu.open(player);
    }
}