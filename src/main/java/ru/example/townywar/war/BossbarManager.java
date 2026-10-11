package ru.example.townywar.war;

import com.palmergames.bukkit.towny.object.Town;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.config.ConfigManager;
import ru.example.townywar.util.TimeUtil;
import solar.squares.pixelwidth.PixelWidthSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BossbarManager implements Listener {

    private static final int MAX_NAME_LENGTH = 16;
    private static final int MAX_PADDING_SPACES = 30;

    private final TownyWarPlugin plugin;
    private final WarManager warManager;
    private final ConfigManager config;
    private final PixelWidthSource widthSource;

    private final Map<UUID, BarState> states = new ConcurrentHashMap<>();
    private BukkitTask ticker;

    public BossbarManager(TownyWarPlugin plugin, WarManager warManager, ConfigManager config) {
        this.plugin = plugin; this.warManager = warManager; this.config = config;
        this.widthSource = PixelWidthSource.pixelWidth();
    }

    public void start() {
        for (String name : new String[]{
                "solar.squares.pixelwidth",
                "ru.example.townywar.libs.pixelwidth"
        }) {
            Logger.getLogger(name).setLevel(Level.SEVERE);
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (Map.Entry<UUID, BarState> e : states.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.hideBossBar(e.getValue().bar);
        }
        states.clear();
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) tickPlayer(p);
    }

    private void tickPlayer(Player player) {
        Town town = plugin.getTownyHook().getTownOf(player);
        War war = town == null ? null : warManager.getWar(town.getName());

        if (town == null || war == null || war.getPhase() == WarPhase.PREPARATION || war.getPhase() == WarPhase.ENDED) {
            hide(player);
            return;
        }

        switch (war.getPhase()) {
            case COMBAT -> tickCombat(player, war, town.getName());
            case NEGOTIATION -> tickNegotiation(player, war, town);
            default -> hide(player);
        }
    }

    private void tickCombat(Player player, War war, String viewerTownName) {
        // Warshop: перемирие — отдельный боссбар с таймером.
        if (plugin.getWarshopManager() != null
                && plugin.getWarshopManager().isTruceActive(war.getId())) {
            long remaining = plugin.getWarshopManager().truceRemainingMs(war.getId());
            Component name = Component.text()
                    .append(Component.text("⏸ ", NamedTextColor.GOLD))
                    .append(Component.text("Перемирие: ", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))
                    .append(Component.text(TimeUtil.format(remaining), NamedTextColor.YELLOW))
                    .build();
            float progress = Math.max(0f, Math.min(1f,
                    remaining / (float) (config.getTruceDurationMin() * 60_000L)));

            BarState st = states.get(player.getUniqueId());
            if (st == null) {
                st = new BarState(BossBar.bossBar(name, progress, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10));
                states.put(player.getUniqueId(), st);
                player.showBossBar(st.bar);
            } else {
                st.bar.name(name);
                st.bar.progress(progress);
                st.bar.color(BossBar.Color.YELLOW);
                st.bar.overlay(BossBar.Overlay.NOTCHED_10);
            }
            st.phase = WarPhase.COMBAT;
            st.warId = war.getId();
            st.ownPoints = -1;
            st.enemyPoints = -1;
            st.ownName = "";
            st.enemyName = "";
            return;
        }

        boolean viewerIsAttacker = war.getAttackerTown().equalsIgnoreCase(viewerTownName);
        String ownName = viewerIsAttacker ? war.getAttackerTown() : war.getDefenderTown();
        String enemyName = viewerIsAttacker ? war.getDefenderTown() : war.getAttackerTown();
        int ownPoints = Math.max(0, viewerIsAttacker ? war.getAttackerPoints() : war.getDefenderPoints());
        int enemyPoints = Math.max(0, viewerIsAttacker ? war.getDefenderPoints() : war.getAttackerPoints());

        BarState st = states.get(player.getUniqueId());

        boolean dirty = st == null
                || st.phase != WarPhase.COMBAT
                || st.warId != war.getId()
                || st.ownPoints != ownPoints
                || st.enemyPoints != enemyPoints
                || !ownName.equals(st.ownName)
                || !enemyName.equals(st.enemyName);

        if (!dirty) return;

        Component name = buildCombatName(ownName, enemyName, ownPoints, enemyPoints);

        BossBar.Color color;
        if (ownPoints > enemyPoints) color = BossBar.Color.GREEN;
        else if (enemyPoints > ownPoints) color = BossBar.Color.RED;
        else color = BossBar.Color.YELLOW;

        if (st == null) {
            st = new BarState(BossBar.bossBar(name, 1.0f, color, BossBar.Overlay.PROGRESS));
            states.put(player.getUniqueId(), st);
            player.showBossBar(st.bar);
        } else {
            st.bar.name(name);
            st.bar.color(color);
            st.bar.progress(1.0f);
        }

        st.phase = WarPhase.COMBAT;
        st.warId = war.getId();
        st.ownPoints = ownPoints;
        st.enemyPoints = enemyPoints;
        st.ownName = ownName;
        st.enemyName = enemyName;
    }

    private void tickNegotiation(Player player, War war, Town town) {
        String winner = war.getWinnerTown();
        if (winner == null || !town.getName().equalsIgnoreCase(winner)
                || !plugin.getTownyHook().isMayor(town, player)) {
            hide(player);
            return;
        }

        long remainingMs = war.getNegotiationEndsAt() == null ? 0L
                : war.getNegotiationEndsAt() - System.currentTimeMillis();

        Component name = Component.text()
                .append(Component.text("⚔ ", NamedTextColor.GOLD))
                .append(Component.text("Победа! ", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))
                .append(Component.text("Выберите исход: ", NamedTextColor.WHITE))
                .append(Component.text("/t negotiation", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true))
                .append(Component.text("  ⏳ " + TimeUtil.format(remainingMs), NamedTextColor.GOLD))
                .build();

        float progress = Math.max(0f, Math.min(1f,
                remainingMs / (float) (config.getNegotiationMinutes() * 60_000L)));

        BarState st = states.get(player.getUniqueId());
        if (st == null) {
            st = new BarState(BossBar.bossBar(name, progress, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10));
            states.put(player.getUniqueId(), st);
            player.showBossBar(st.bar);
        } else {
            st.bar.name(name);
            st.bar.progress(progress);
            st.bar.color(BossBar.Color.YELLOW);
            st.bar.overlay(BossBar.Overlay.NOTCHED_10);
        }

        st.phase = WarPhase.NEGOTIATION;
        st.warId = war.getId();
        st.ownPoints = 0;
        st.enemyPoints = 0;
        st.ownName = winner;
        st.enemyName = winner;
    }

    private Component buildCombatName(String ownName, String enemyName, int ownPoints, int enemyPoints) {
        String ownShort = truncate(ownName, MAX_NAME_LENGTH);
        String enemyShort = truncate(enemyName, MAX_NAME_LENGTH);

        Component leftPart = Component.text()
                .append(Component.text(ownShort, NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" [" + ownPoints + "]", NamedTextColor.GREEN))
                .build();

        Component rightPart = Component.text()
                .append(Component.text("[" + enemyPoints + "] ", NamedTextColor.RED))
                .append(Component.text(enemyShort, NamedTextColor.RED).decoration(TextDecoration.BOLD, true))
                .build();

        float leftWidth = safeWidth(leftPart);
        float rightWidth = safeWidth(rightPart);
        float diff = Math.abs(leftWidth - rightWidth);

        float spaceWidth = safeWidth(Component.text(" "));
        if (spaceWidth <= 0) spaceWidth = 4f;

        int spacesToAdd = Math.round(diff / spaceWidth);
        if (spacesToAdd < 0) spacesToAdd = 0;
        if (spacesToAdd > MAX_PADDING_SPACES) spacesToAdd = MAX_PADDING_SPACES;

        Component leftPadded = leftPart;
        Component rightPadded = rightPart;

        if (leftWidth < rightWidth && spacesToAdd > 0) {
            leftPadded = Component.text()
                    .append(Component.text(" ".repeat(spacesToAdd), NamedTextColor.WHITE))
                    .append(leftPart)
                    .build();
        } else if (rightWidth < leftWidth && spacesToAdd > 0) {
            rightPadded = Component.text()
                    .append(rightPart)
                    .append(Component.text(" ".repeat(spacesToAdd), NamedTextColor.WHITE))
                    .build();
        }

        return Component.text()
                .append(leftPadded)
                .append(Component.text("  ⚔  ", NamedTextColor.DARK_GRAY))
                .append(rightPadded)
                .build();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    private float safeWidth(Component component) {
        try {
            return widthSource.width(component);
        } catch (Throwable t) {
            return 0f;
        }
    }

    private void hide(Player player) {
        BarState st = states.remove(player.getUniqueId());
        if (st != null) player.hideBossBar(st.bar);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    private static final class BarState {
        final BossBar bar;
        long warId;
        int ownPoints;
        int enemyPoints;
        WarPhase phase;
        String ownName = "";
        String enemyName = "";

        BarState(BossBar bar) {
            this.bar = bar;
            this.phase = WarPhase.ENDED;
            this.warId = -1L;
        }
    }
}