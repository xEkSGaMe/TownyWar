package ru.example.townywar.command;

import org.bukkit.entity.Player;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.menu.WarListMenu;

public final class WarListCommand {

    private final TownyWarPlugin plugin;
    private final WarListMenu menu;

    public WarListCommand(TownyWarPlugin plugin, WarListMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    public void execute(Player player) {
        if (!player.hasPermission("townywar.list")) {
            plugin.getMessages().send(player, "no-permission");
            return;
        }
        menu.open(player);
    }
}