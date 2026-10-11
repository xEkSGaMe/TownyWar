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
import org.bukkit.scheduler.BukkitTask;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обрабатывает {@code /twar declare <город>} для обычных игроков.
 * <p>
 * Показывает в чате кликабельное подтверждение (✔/✖) через
 * {@link ClickEvent#callback}. Таймаут — 30 секунд.
 * <p>
 * Все проверки (mayor, same-nation, neutral, at-war, возраст города)
 * выполняются ДО показа подтверждения и повторно — при нажатии ✔,
 * потому что состояние могло измениться за 30 секунд.
 */
public final class WarDeclareCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final long CONFIRM_TIMEOUT_MS = 30_000L;

    private final TownyWarPlugin plugin;
    private final WarManager warManager;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final BukkitTask cleanupTask;

    public WarDeclareCommand(TownyWarPlugin plugin, WarManager warManager) {
        this.plugin = plugin;
        this.warManager = warManager;
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::cleanupExpired, 100L, 100L);
    }

    public void shutdown() {
        if (cleanupTask != null) cleanupTask.cancel();
        pending.clear();
    }

    public void execute(Player player, String targetName) {
        if (!player.hasPermission("townywar.declare")) {
            plugin.getMessages().send(player, "no-permission");
            return;
        }

        if (pending.containsKey(player.getUniqueId())) {
            player.sendActionBar(MM.deserialize(
                    "<red>У вас уже есть активное подтверждение. Нажмите ✔ или ✖.</red>"));
            return;
        }

        Town own = plugin.getTownyHook().getTownOf(player);
        if (own == null) {
            plugin.getMessages().send(player, "not-in-town");
            return;
        }
        if (!plugin.getTownyHook().isMayor(own, player)) {
            plugin.getMessages().send(player, "not-mayor");
            return;
        }

        Town target = plugin.getTownyHook().getTown(targetName);
        if (target == null) {
            plugin.getMessages().send(player, "town-not-found", "town", targetName);
            return;
        }
        if (target.getName().equalsIgnoreCase(own.getName())) {
            plugin.getMessages().send(player, "self-war");
            return;
        }
        if (plugin.getTownyHook().isSameNation(own, target)) {
            plugin.getMessages().send(player, "same-nation");
            return;
        }
        if (plugin.getTownyHook().isNeutral(target) || plugin.getTownyHook().isNeutral(own)) {
            plugin.getMessages().send(player, "neutral");
            return;
        }
        if (warManager.isAtWar(own.getName())) {
            plugin.getMessages().send(player, "already-at-war");
            return;
        }
        if (warManager.isAtWar(target.getName())) {
            plugin.getMessages().send(player, "target-at-war");
            return;
        }

        long ageMs = System.currentTimeMillis() - own.getRegistered();
        long requiredMs = plugin.getConfigManager().getMinTownAgeDays() * 86_400_000L;
        if (ageMs < requiredMs) {
            long leftDays = (requiredMs - ageMs) / 86_400_000L + 1;
            plugin.getMessages().send(player, "town-too-young", "days", String.valueOf(leftDays));
            return;
        }

        long expiresAt = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS;
        pending.put(player.getUniqueId(),
                new Pending(own.getName(), target.getName(), expiresAt));

        sendConfirmPrompt(player, own.getName(), target.getName());
    }

    // ---------- UI ----------

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

    // ---------- Handlers ----------

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

        Town own = plugin.getTownyHook().getTownOf(player);
        if (own == null || !own.getName().equalsIgnoreCase(p.attackerTown())) {
            player.sendActionBar(MM.deserialize("<red>Вы больше не мэр этого города.</red>"));
            return;
        }
        if (!plugin.getTownyHook().isMayor(own, player)) {
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

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }

    private record Pending(String attackerTown, String targetTown, long expiresAt) { }
}