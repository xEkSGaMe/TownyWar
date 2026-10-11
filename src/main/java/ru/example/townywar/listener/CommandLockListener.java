package ru.example.townywar.listener;

import com.palmergames.bukkit.towny.object.Town;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarManager;

import java.util.List;
import java.util.Locale;

public final class CommandLockListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final TownyWarPlugin plugin;
    private final WarManager warManager;

    public CommandLockListener(TownyWarPlugin plugin, WarManager warManager) {
        this.plugin = plugin; this.warManager = warManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (msg.isEmpty() || msg.charAt(0) != '/') return;
        String[] parts = msg.substring(1).split("\\s+");
        if (parts.length < 2) return;
        String root = parts[0].toLowerCase(Locale.ROOT);
        if (!(root.equals("t") || root.equals("town") || root.equals("towny"))) return;

        Player player = event.getPlayer();
        Town town = plugin.getTownyHook().getTownOf(player);
        if (town == null) return;
        War war = warManager.getWar(town.getName());
        if (war == null || !war.isActive()) return;

        String sub = parts[1].toLowerCase(Locale.ROOT);
        String subFull = (parts.length >= 3)
                ? sub + " " + parts[2].toLowerCase(Locale.ROOT)
                : sub;

        List<String> blocked = plugin.getConfigManager().getBlockedCommands();
        String matched = null;
        for (String b : blocked) {
            if (b.equals(sub) || b.equals(subFull)) { matched = b; break; }
        }
        if (matched == null) return;

        event.setCancelled(true);
        player.sendActionBar(MM.deserialize(
                "<red>Команда <yellow>/t " + matched + "</yellow> недоступна во время войны.</red>"));
        plugin.getWarLogRepository().log(war.getId(), player.getName(),
                "COMMAND_BLOCKED", "command=" + msg);
    }
}