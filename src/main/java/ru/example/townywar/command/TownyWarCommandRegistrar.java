package ru.example.townywar.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarManager;
import ru.example.townywar.war.WarPhase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

@SuppressWarnings("UnstableApiUsage")
public final class TownyWarCommandRegistrar {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final SimpleDateFormat DF = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    static {
        DF.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
    }

    public void register(Commands commands) {
        LiteralCommandNode<CommandSourceStack> root = Commands.literal("twar")
                .requires(src -> src.getSender().hasPermission("townywar.admin"))
                .then(Commands.literal("reload").executes(this::reload))
                .then(Commands.literal("help").executes(this::help))
                .then(Commands.literal("admin")
                        .then(Commands.literal("addpoints")
                                .then(Commands.argument("town", StringArgumentType.string())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(this::addPoints))))
                        .then(Commands.literal("removepoints")
                                .then(Commands.argument("town", StringArgumentType.string())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(this::removePoints))))
                        .then(Commands.literal("setpoints")
                                .then(Commands.argument("town", StringArgumentType.string())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(this::setPoints))))
                        .then(Commands.literal("setmaxflags")
                                .then(Commands.argument("town", StringArgumentType.string())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(this::setMaxFlags))))
                        .then(Commands.literal("setbonus")
                                .then(Commands.argument("town", StringArgumentType.string())
                                        .then(Commands.argument("delta", IntegerArgumentType.integer())
                                                .executes(this::setBonus))))
                        .then(Commands.literal("addbonus")
                                .then(Commands.argument("town", StringArgumentType.string())
                                        .then(Commands.argument("delta", IntegerArgumentType.integer())
                                                .executes(this::addBonus))))
                        .then(Commands.literal("getbonus")
                                .then(Commands.argument("town", StringArgumentType.string())
                                        .executes(this::getBonus)))
                        .then(Commands.literal("declare")
                                .then(Commands.argument("attacker", StringArgumentType.string())
                                        .then(Commands.argument("defender", StringArgumentType.string())
                                                .executes(this::adminDeclare))))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("town", StringArgumentType.string())
                                        .executes(this::adminCancel)))
                        .then(Commands.literal("end")
                                .then(Commands.argument("town", StringArgumentType.string())
                                        .executes(ctx -> adminEnd(ctx, "admin-force-end"))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> adminEnd(ctx, StringArgumentType.getString(ctx, "reason"))))))
                        .then(Commands.literal("phase")
                                .then(Commands.argument("town", StringArgumentType.string())
                                        .then(Commands.argument("phase", StringArgumentType.word())
                                                .suggests(PHASE_SUGGESTIONS)
                                                .executes(this::adminPhase))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("town", StringArgumentType.string())
                                        .executes(this::adminInfo)))
                        .then(Commands.literal("list").executes(this::adminList)))
                .build();

        commands.register(root, "Управление TownyWar");
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        TownyWarPlugin.get().getConfigManager().reload();
        TownyWarPlugin.get().getMessages().reload();
        ctx.getSource().getSender().sendMessage(MM.deserialize("<green>Конфиг TownyWar перезагружен.</green>"));
        return 1;
    }

    private int help(CommandContext<CommandSourceStack> ctx) {
        CommandSender s = ctx.getSource().getSender();
        s.sendMessage(MM.deserialize("<gold><bold>TownyWar</bold></gold> <gray>— команды:</gray>"));
        s.sendMessage(MM.deserialize("<yellow>/twar reload</yellow> <gray>— перечитать конфиг.</gray>"));
        s.sendMessage(MM.deserialize("<yellow>/twar help</yellow> <gray>— это меню.</gray>"));
        s.sendMessage(MM.deserialize("<yellow>/twar admin addpoints <город> <n></yellow>"));
        s.sendMessage(MM.deserialize("<yellow>/twar admin removepoints <город> <n></yellow>"));
        s.sendMessage(MM.deserialize("<yellow>/twar admin setpoints <город> <n></yellow>"));
        s.sendMessage(MM.deserialize("<yellow>/twar admin setmaxflags <город> <n></yellow>"));
        s.sendMessage(MM.deserialize("<yellow>/twar admin setbonus <город> <delta></yellow>"));
        s.sendMessage(MM.deserialize("<yellow>/twar admin addbonus <город> <delta></yellow>"));
        s.sendMessage(MM.deserialize("<yellow>/twar admin getbonus <город></yellow>"));
        s.sendMessage(MM.deserialize("<yellow>/twar admin declare <атакующий> <защитник></yellow>"));
        s.sendMessage(MM.deserialize("<yellow>/twar admin cancel <город></yellow>"));
        s.sendMessage(MM.deserialize("<yellow>/twar admin end <город> [причина]</yellow>"));
        s.sendMessage(MM.deserialize("<yellow>/twar admin phase <город> <PREPARATION|COMBAT|NEGOTIATION></yellow>"));
        s.sendMessage(MM.deserialize("<yellow>/twar admin info <город></yellow>"));
        s.sendMessage(MM.deserialize("<yellow>/twar admin list</yellow>"));
        return 1;
    }

    private int addPoints(CommandContext<CommandSourceStack> ctx) {
        String town = StringArgumentType.getString(ctx, "town");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        WarManager wm = TownyWarPlugin.get().getWarManager();
        if (!wm.isAtWar(town)) { notAtWar(ctx, town); return 0; }
        wm.addPointsAdmin(town, amount, actor(ctx));
        ctx.getSource().getSender().sendMessage(MM.deserialize("<green>Городу <yellow>" + town + "</yellow> начислено <yellow>" + amount + "</yellow> очков.</green>"));
        return 1;
    }

    private int removePoints(CommandContext<CommandSourceStack> ctx) {
        String town = StringArgumentType.getString(ctx, "town");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        WarManager wm = TownyWarPlugin.get().getWarManager();
        if (!wm.isAtWar(town)) { notAtWar(ctx, town); return 0; }
        wm.removePointsAdmin(town, amount, actor(ctx));
        ctx.getSource().getSender().sendMessage(MM.deserialize("<red>У города <yellow>" + town + "</yellow> снято <yellow>" + amount + "</yellow> очков.</red>"));
        return 1;
    }

    private int setPoints(CommandContext<CommandSourceStack> ctx) {
        String town = StringArgumentType.getString(ctx, "town");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        WarManager wm = TownyWarPlugin.get().getWarManager();
        if (!wm.isAtWar(town)) { notAtWar(ctx, town); return 0; }
        wm.setPointsAdmin(town, amount, actor(ctx));
        ctx.getSource().getSender().sendMessage(MM.deserialize("<green>Очки города <yellow>" + town + "</yellow> = <yellow>" + amount + "</yellow>.</green>"));
        return 1;
    }

    private int setMaxFlags(CommandContext<CommandSourceStack> ctx) {
        String town = StringArgumentType.getString(ctx, "town");
        int count = IntegerArgumentType.getInteger(ctx, "count");
        WarManager wm = TownyWarPlugin.get().getWarManager();

        if (!wm.setMaxConcurrentFlags(town, count, actor(ctx))) {
            ctx.getSource().getSender().sendMessage(MM.deserialize(
                    "<red>Не удалось установить лимит: город <yellow>" + town
                            + "</yellow> не найден в Towny.</red>"));
            return 0;
        }

        int actual = wm.getMaxConcurrentFlags(town);
        String note = actual != count
                ? " <gray>(запрошено " + count + ", кламп " + actual + ")</gray>"
                : "";
        ctx.getSource().getSender().sendMessage(MM.deserialize(
                "<green>Для города <yellow>" + town + "</yellow> лимит флагов: <yellow>"
                        + actual + "</yellow>." + note + "</green>"));
        return 1;
    }

    private int setBonus(CommandContext<CommandSourceStack> ctx) {
        String town = StringArgumentType.getString(ctx, "town");
        int delta = IntegerArgumentType.getInteger(ctx, "delta");
        WarManager wm = TownyWarPlugin.get().getWarManager();
        if (!wm.setStartingBonus(town, delta, actor(ctx))) {
            int min = TownyWarPlugin.get().getConfigManager().getMinTotalPoints();
            int max = TownyWarPlugin.get().getConfigManager().getMaxTotalPoints();
            int base = TownyWarPlugin.get().getConfigManager().getDefaultPoints();
            ctx.getSource().getSender().sendMessage(MM.deserialize(
                    "<red>Не удалось установить: либо город <yellow>" + town
                            + "</yellow> не найден, либо итог (" + (base + delta)
                            + ") вышел за [" + min + "; " + max + "].</red>"));
            return 0;
        }
        int total = wm.getStartingPoints(town);
        String sign = delta >= 0 ? "+" : "";
        ctx.getSource().getSender().sendMessage(MM.deserialize(
                "<green>Стартовые очки <yellow>" + town + "</yellow>: <yellow>" + total
                        + "</yellow> (бонус <white>" + sign + delta + "</white>).</green>"));
        return 1;
    }

    private int addBonus(CommandContext<CommandSourceStack> ctx) {
        String town = StringArgumentType.getString(ctx, "town");
        int delta = IntegerArgumentType.getInteger(ctx, "delta");
        WarManager wm = TownyWarPlugin.get().getWarManager();

        if (!wm.addStartingBonus(town, delta, actor(ctx))) {
            int min = TownyWarPlugin.get().getConfigManager().getMinTotalPoints();
            int max = TownyWarPlugin.get().getConfigManager().getMaxTotalPoints();
            int base = TownyWarPlugin.get().getConfigManager().getDefaultPoints();
            int cur = wm.getStartingBonus(town);
            ctx.getSource().getSender().sendMessage(MM.deserialize(
                    "<red>Не удалось прибавить: либо город <yellow>" + town
                            + "</yellow> не найден, либо итог (" + (base + cur + delta)
                            + ") вышел за [" + min + "; " + max + "].</red>"));
            return 0;
        }

        int total = wm.getStartingPoints(town);
        String sign = delta >= 0 ? "+" : "";
        ctx.getSource().getSender().sendMessage(MM.deserialize(
                "<green>Стартовые очки <yellow>" + town + "</yellow>: <yellow>" + total
                        + "</yellow> (бонус <white>" + sign + delta + "</white>).</green>"));
        return 1;
    }

    private int getBonus(CommandContext<CommandSourceStack> ctx) {
        String town = StringArgumentType.getString(ctx, "town");
        WarManager wm = TownyWarPlugin.get().getWarManager();
        int base = TownyWarPlugin.get().getConfigManager().getDefaultPoints();
        int bonus = wm.getStartingBonus(town);
        int total = wm.getStartingPoints(town);
        String sign = bonus >= 0 ? "+" : "";
        ctx.getSource().getSender().sendMessage(MM.deserialize(
                "<gray>Город <yellow>" + town + "</yellow>: база <white>" + base
                        + "</white>, бонус <white>" + sign + bonus
                        + "</white>, итог <gold>" + total + "</gold>.</gray>"));
        return 1;
    }

    /**
     * B5: перед вызовом WarManager.adminDeclare проверяем, что оба города
     * существуют в Towny, не совпадают и не находятся в активной войне.
     */
    private int adminDeclare(CommandContext<CommandSourceStack> ctx) {
        String attacker = StringArgumentType.getString(ctx, "attacker");
        String defender = StringArgumentType.getString(ctx, "defender");

        var hook = TownyWarPlugin.get().getTownyHook();
        if (hook.getTown(attacker) == null) {
            ctx.getSource().getSender().sendMessage(
                    MM.deserialize("<red>Город <yellow>" + attacker + "</yellow> не найден.</red>"));
            return 0;
        }
        if (hook.getTown(defender) == null) {
            ctx.getSource().getSender().sendMessage(
                    MM.deserialize("<red>Город <yellow>" + defender + "</yellow> не найден.</red>"));
            return 0;
        }
        if (attacker.equalsIgnoreCase(defender)) {
            ctx.getSource().getSender().sendMessage(
                    MM.deserialize("<red>Атакующий и защитник совпадают.</red>"));
            return 0;
        }

        WarManager wm = TownyWarPlugin.get().getWarManager();
        if (wm.isAtWar(attacker) || wm.isAtWar(defender)) {
            ctx.getSource().getSender().sendMessage(
                    MM.deserialize("<red>Один из городов уже в войне.</red>"));
            return 0;
        }
        War w = wm.adminDeclare(attacker, defender, actor(ctx));
        if (w == null) {
            ctx.getSource().getSender().sendMessage(
                    MM.deserialize("<red>Не удалось объявить войну.</red>"));
            return 0;
        }
        ctx.getSource().getSender().sendMessage(MM.deserialize(
                "<green>Война: <yellow>" + attacker + "</yellow> → <yellow>" + defender
                        + "</yellow> (id=" + w.getId() + ").</green>"));
        return 1;
    }

    private int adminCancel(CommandContext<CommandSourceStack> ctx) {
        String town = StringArgumentType.getString(ctx, "town");
        WarManager wm = TownyWarPlugin.get().getWarManager();
        War w = wm.getWar(town);
        if (w == null) { notAtWar(ctx, town); return 0; }
        wm.endWar(w, null, "admin-cancel", false);
        ctx.getSource().getSender().sendMessage(MM.deserialize("<yellow>Война с участием " + town + " отменена тихо.</yellow>"));
        return 1;
    }

    private int adminEnd(CommandContext<CommandSourceStack> ctx, String reason) {
        String town = StringArgumentType.getString(ctx, "town");
        WarManager wm = TownyWarPlugin.get().getWarManager();
        if (!wm.isAtWar(town)) { notAtWar(ctx, town); return 0; }
        wm.forceEnd(town, reason, actor(ctx));
        ctx.getSource().getSender().sendMessage(MM.deserialize("<yellow>Война с участием " + town + " завершена (" + reason + ").</yellow>"));
        return 1;
    }

    private int adminPhase(CommandContext<CommandSourceStack> ctx) {
        String town = StringArgumentType.getString(ctx, "town");
        String phaseName = StringArgumentType.getString(ctx, "phase").toUpperCase(Locale.ROOT);
        WarPhase phase;
        try { phase = WarPhase.valueOf(phaseName); }
        catch (IllegalArgumentException ex) {
            ctx.getSource().getSender().sendMessage(
                    MM.deserialize("<red>Неверная фаза. PREPARATION, COMBAT, NEGOTIATION.</red>"));
            return 0;
        }
        if (phase == WarPhase.ENDED) {
            ctx.getSource().getSender().sendMessage(
                    MM.deserialize("<red>ENDED задаётся через /twar admin end.</red>"));
            return 0;
        }
        WarManager wm = TownyWarPlugin.get().getWarManager();
        War w = wm.getWar(town);
        if (w == null) { notAtWar(ctx, town); return 0; }

        if (phase == WarPhase.NEGOTIATION && w.getWinnerTown() == null
                && w.getAttackerPoints() == w.getDefenderPoints()) {
            ctx.getSource().getSender().sendMessage(MM.deserialize(
                    "<red>Очки сторон равны — нельзя войти в NEGOTIATION. Сначала задайте очки через /twar admin setpoints.</red>"));
            return 0;
        }

        if (!wm.setPhase(town, phase, actor(ctx))) { notAtWar(ctx, town); return 0; }
        ctx.getSource().getSender().sendMessage(MM.deserialize(
                "<green>Фаза <yellow>" + town + "</yellow> → <yellow>" + phase.name() + "</yellow>.</green>"));
        return 1;
    }

    private int adminInfo(CommandContext<CommandSourceStack> ctx) {
        String town = StringArgumentType.getString(ctx, "town");
        War w = TownyWarPlugin.get().getWarManager().getWar(town);
        if (w == null) { notAtWar(ctx, town); return 0; }
        CommandSender s = ctx.getSource().getSender();
        s.sendMessage(MM.deserialize("<gold>Война #" + w.getId() + "</gold>"));
        s.sendMessage(MM.deserialize("<gray>Атакующий: <yellow>" + w.getAttackerTown() + "</yellow> — <red>" + w.getAttackerPoints() + "</red>"));
        s.sendMessage(MM.deserialize("<gray>Защитник: <yellow>" + w.getDefenderTown() + "</yellow> — <red>" + w.getDefenderPoints() + "</red>"));
        s.sendMessage(MM.deserialize("<gray>Фаза: <yellow>" + w.getPhase().name() + "</yellow>"));
        s.sendMessage(MM.deserialize("<gray>Объявлена: <yellow>" + DF.format(new Date(w.getDeclaredAt())) + "</yellow>"));
        s.sendMessage(MM.deserialize("<gray>Преп до: <yellow>" + DF.format(new Date(w.getPrepEndsAt())) + "</yellow>"));
        if (w.getCombatStartedAt() != null) s.sendMessage(MM.deserialize("<gray>Бой с: <yellow>" + DF.format(new Date(w.getCombatStartedAt())) + "</yellow>"));
        if (w.getWinnerTown() != null) s.sendMessage(MM.deserialize("<gray>Победитель: <yellow>" + w.getWinnerTown() + "</yellow>"));
        return 1;
    }

    private int adminList(CommandContext<CommandSourceStack> ctx) {
        var wars = TownyWarPlugin.get().getWarManager().snapshotWars();
        CommandSender s = ctx.getSource().getSender();
        if (wars.isEmpty()) { s.sendMessage(MM.deserialize("<gray>Активных войн нет.</gray>")); return 1; }
        s.sendMessage(MM.deserialize("<gold>Активных войн: " + wars.size() + "</gold>"));
        for (War w : wars) {
            s.sendMessage(MM.deserialize("<yellow>#" + w.getId() + "</yellow> <gray>"
                    + w.getAttackerTown() + " (" + w.getAttackerPoints() + ") vs "
                    + w.getDefenderTown() + " (" + w.getDefenderPoints() + ") — " + w.getPhase().name() + "</gray>"));
        }
        return 1;
    }

    private static final SuggestionProvider<CommandSourceStack> PHASE_SUGGESTIONS = (ctx, b) -> {
        b.suggest("PREPARATION"); b.suggest("COMBAT"); b.suggest("NEGOTIATION");
        return b.buildFuture();
    };

    private static String actor(CommandContext<CommandSourceStack> ctx) {
        var src = ctx.getSource();
        return src.getExecutor() != null ? src.getExecutor().getName() : "CONSOLE";
    }

    private static void notAtWar(CommandContext<CommandSourceStack> ctx, String town) {
        ctx.getSource().getSender().sendMessage(MM.deserialize("<red>Город <yellow>" + town + "</yellow> не участвует в активной войне.</red>"));
    }
}