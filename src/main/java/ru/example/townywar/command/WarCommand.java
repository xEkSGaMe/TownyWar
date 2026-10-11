package ru.example.townywar.command;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.entity.Player;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarManager;
import ru.example.townywar.war.WarPhase;

import java.util.Locale;

public final class WarCommand {

    private final TownyWarPlugin plugin;
    private final WarManager warManager;

    public WarCommand(TownyWarPlugin plugin, WarManager warManager) {
        this.plugin = plugin;
        this.warManager = warManager;
    }

    public void execute(Player p, String[] args) {
        if (args.length == 0) {
            plugin.getMessages().send(p, "usage-war");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "cancel" -> cancel(p);
            case "glow"   -> glow(p, args);
            default       -> declare(p, args[0]);
        }
    }

    // --- /t war <town> ---------------------------------------------------------

    private void declare(Player p, String targetName) {
        if (!p.hasPermission("townywar.declare")) {
            plugin.getMessages().send(p, "no-permission");
            return;
        }

        Town own = plugin.getTownyHook().getTownOf(p);
        if (own == null) {
            plugin.getMessages().send(p, "not-in-town");
            return;
        }
        if (!plugin.getTownyHook().isMayor(own, p)) {
            plugin.getMessages().send(p, "not-mayor");
            return;
        }

        Town target = plugin.getTownyHook().getTown(targetName);
        if (target == null) {
            plugin.getMessages().send(p, "town-not-found", "town", targetName);
            return;
        }
        if (target.getName().equalsIgnoreCase(own.getName())) {
            plugin.getMessages().send(p, "self-war");
            return;
        }
        if (plugin.getTownyHook().isSameNation(own, target)) {
            plugin.getMessages().send(p, "same-nation");
            return;
        }
        if (plugin.getTownyHook().isNeutral(target) || plugin.getTownyHook().isNeutral(own)) {
            plugin.getMessages().send(p, "neutral");
            return;
        }
        if (warManager.isAtWar(own.getName())) {
            plugin.getMessages().send(p, "already-at-war");
            return;
        }
        if (warManager.isAtWar(target.getName())) {
            plugin.getMessages().send(p, "target-at-war");
            return;
        }

        long ageMs = System.currentTimeMillis() - own.getRegistered();
        long requiredMs = plugin.getConfigManager().getMinTownAgeDays() * 86_400_000L;
        if (ageMs < requiredMs) {
            long leftDays = (requiredMs - ageMs) / 86_400_000L + 1;
            plugin.getMessages().send(p, "town-too-young", "days", String.valueOf(leftDays));
            return;
        }

        War w = warManager.declare(own.getName(), target.getName());
        if (w == null) {
            plugin.getMessages().send(p, "declare-failed");
            return;
        }

        plugin.getMessages().send(p, "war-declared", "target", target.getName());

        for (Resident r : target.getResidents()) {
            Player online = r.getPlayer();
            if (online != null) {
                plugin.getMessages().send(online, "war-declared-defender",
                        "attacker", own.getName());
            }
        }
    }

    // --- /t war cancel ---------------------------------------------------------

    private void cancel(Player p) {
        if (!p.hasPermission("townywar.cancel")) {
            plugin.getMessages().send(p, "no-permission");
            return;
        }
        Town own = plugin.getTownyHook().getTownOf(p);
        if (own == null) {
            plugin.getMessages().send(p, "not-in-town");
            return;
        }
        War w = warManager.getWar(own.getName());
        if (w == null || !w.getAttackerTown().equalsIgnoreCase(own.getName())) {
            plugin.getMessages().send(p, "no-war-to-cancel");
            return;
        }
        if (w.getPhase() != WarPhase.PREPARATION) {
            plugin.getMessages().send(p, "cannot-cancel-phase");
            return;
        }
        warManager.cancel(w, "cancelled-by-attacker");
        plugin.getMessages().send(p, "war-cancelled");
    }

    // --- /t war glow on|off ----------------------------------------------------

    private void glow(Player p, String[] args) {
        if (!p.hasPermission("townywar.glow")) {
            plugin.getMessages().send(p, "no-permission");
            return;
        }
        if (!plugin.getGlowManager().isAvailable()) {
            p.sendActionBar(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize("<red>PacketEvents не установлен — свечение недоступно.</red>"));
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(p, "usage-glow");
            return;
        }
        boolean on = args[1].equalsIgnoreCase("on");
        plugin.getGlowManager().toggle(p, on);
        plugin.getMessages().send(p, on ? "glow-on" : "glow-off");
    }
}