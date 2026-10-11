package ru.example.townywar.command;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarManager;
import ru.example.townywar.war.WarPhase;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WarCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long CONFIRM_TIMEOUT_MS = 30_000L;

    private final TownyWarPlugin plugin;
    private final WarManager warManager;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public WarCommand(TownyWarPlugin plugin, WarManager warManager) {
        this.plugin = plugin;
        this.warManager = warManager;

        // Чистим истёкшие промпты. Таск привязан к plugin — Bukkit сам его
        // отменит при выключении, отдельный shutdown() не нужен.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            pending.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        }, 100L, 100L);
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

        if (pending.containsKey(p.getUniqueId())) {
            p.sendActionBar(MM.deserialize(
                    "<red>У вас уже есть активное подтверждение. Нажмите ✔ или ✖.</red>"));
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

        long expiresAt = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS;
        pending.put(p.getUniqueId(), new Pending(own.getName(), target.getName(), expiresAt));
        sendConfirmPrompt(p, own.getName(), target.getName());
    }

    private void sendConfirmPrompt(Player player, String attackerName, String targetName) {
        Component separator = MM.deserialize(
                "<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray>");

        Component yes = Component.text()
                .append(Component.text("✔", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(" Подтвердить", NamedTextColor.GREEN))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience instanceof Player p) handleConfirm(p);
                }))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Объявить войну городу ", NamedTextColor.GRAY)
                                .append(Component.text(targetName, NamedTextColor.YELLOW))))
                .build();

        Component no = Component.text()
                .append(Component.text("✖", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" Отменить", NamedTextColor.RED))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience instanceof Player p) handleCancel(p);
                }))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Отменить объявление войны", NamedTextColor.GRAY)))
                .build();

        int timeoutSec = (int) (CONFIRM_TIMEOUT_MS / 1000);

        player.sendMessage(Component.empty());
        player.sendMessage(separator);
        player.sendMessage(MM.deserialize("<gold><bold>⚔ Объявление войны</bold></gold>"));
        player.sendMessage(Component.empty());
        player.sendMessage(MM.deserialize(
                "<gray>Атакующий: <yellow>" + MM.escapeTags(attackerName) + "</yellow></gray>"));
        player.sendMessage(MM.deserialize(
                "<gray>Цель: <yellow>" + MM.escapeTags(targetName) + "</yellow></gray>"));
        player.sendMessage(MM.deserialize(
                "<gray>Подтверждение: <yellow>" + timeoutSec + " сек</yellow>.</gray>"));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text()
                .append(Component.text("  "))
                .append(yes)
                .append(Component.text("        "))
                .append(no)
                .build());
        player.sendMessage(separator);
        player.sendMessage(Component.empty());
    }

    private void handleConfirm(Player player) {
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            player.sendActionBar(MM.deserialize(
                    "<red>Подтверждение истекло или уже обработано.</red>"));
            return;
        }
        if (p.expiresAt() < System.currentTimeMillis()) {
            player.sendActionBar(MM.deserialize("<red>Время подтверждения истекло.</red>"));
            return;
        }

        // Ре-валидация: за 30 секунд состояние могло измениться.
        Town own = plugin.getTownyHook().getTownOf(player);
        if (own == null || !own.getName().equalsIgnoreCase(p.attackerTown())
                || !plugin.getTownyHook().isMayor(own, player)) {
            player.sendActionBar(MM.deserialize("<red>Вы больше не мэр этого города.</red>"));
            return;
        }
        if (warManager.isAtWar(own.getName())) {
            plugin.getMessages().send(player, "already-at-war");
            return;
        }

        Town target = plugin.getTownyHook().getTown(p.targetTown());
        if (target == null) {
            plugin.getMessages().send(player, "town-not-found", "town", p.targetTown());
            return;
        }
        if (warManager.isAtWar(target.getName())) {
            plugin.getMessages().send(player, "target-at-war");
            return;
        }

        War w = warManager.declare(own.getName(), target.getName());
        if (w == null) {
            plugin.getMessages().send(player, "declare-failed");
            return;
        }

        plugin.getMessages().send(player, "war-declared", "target", target.getName());

        for (Resident r : target.getResidents()) {
            Player online = r.getPlayer();
            if (online != null) {
                plugin.getMessages().send(online, "war-declared-defender",
                        "attacker", own.getName());
            }
        }
    }

    private void handleCancel(Player player) {
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            player.sendActionBar(MM.deserialize("<red>Нечего отменять.</red>"));
            return;
        }
        player.sendMessage(MM.deserialize("<gray>Объявление войны отменено.</gray>"));
    }

    private record Pending(String attackerTown, String targetTown, long expiresAt) { }

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
            p.sendActionBar(MM.deserialize(
                    "<red>PacketEvents не установлен — свечение недоступно.</red>"));
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