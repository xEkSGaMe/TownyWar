package ru.example.townywar.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.negotiation.NegotiationMenu;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarPhase;

public final class NegotiationCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final TownyWarPlugin plugin;
    private final NegotiationMenu menu;

    public NegotiationCommand(TownyWarPlugin plugin, NegotiationMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    public void execute(Player player) {
        if (!player.hasPermission("townywar.negotiation")) {
            plugin.getMessages().send(player, "no-permission");
            return;
        }
        var town = plugin.getTownyHook().getTownOf(player);
        if (town == null) {
            plugin.getMessages().send(player, "not-in-town");
            return;
        }
        War war = plugin.getWarManager().getWar(town.getName());
        if (war == null || war.getPhase() != WarPhase.NEGOTIATION) {
            player.sendActionBar(MM.deserialize("<red>Нет активных переговоров.</red>"));
            return;
        }
        if (!plugin.getTownyHook().isMayor(town, player)) {
            plugin.getMessages().send(player, "not-mayor");
            return;
        }
        if (war.getWinnerTown() == null || !war.getWinnerTown().equalsIgnoreCase(town.getName())) {
            player.sendActionBar(MM.deserialize("<red>Только победитель проводит переговоры.</red>"));
            return;
        }
        menu.open(player, war);
    }
}