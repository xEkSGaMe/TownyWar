package ru.example.townywar.glow;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.CollisionRule;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.NameTagVisibility;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.OptionData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.integration.TownyHook;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarManager;
import ru.example.townywar.war.WarPhase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class GlowManager implements Listener {

    private static final long TICK_PERIOD = 20L;

    /**
     * B8: префикс команд с namespace плагина. Раньше был "tw_" — коллизия
     * с чужими scoreboard-плагинами, начинающимися на "tw_", теоретически
     * возможна. "townywar_" делает пространство имён явным.
     */
    private static final String TEAM_PREFIX = "twr_";

    private static final NamedTextColor OWN_COLOR = NamedTextColor.GREEN;
    private static final NamedTextColor ENEMY_COLOR = NamedTextColor.RED;

    /**
     * Индекс 0 в Entity metadata — byte flags (см. ClientboundSetEntityDataPacket).
     * <p>
     * ВАЖНО: этот байт — полная замена состояния, а не побитовый OR.
     * Клиент обрабатывает его как «новый снапшот флагов». Если не выставить
     * все биты, соответствующие текущему состоянию игрока, клиент получит
     * противоречивый снапшот и может залипнуть в неверном рендере (например,
     * в невидимости) до следующего «полного» обновления (push/hit/etc.).
     * <p>
     * Поэтому здесь перечислены все используемые клиентом флаги, и
     * {@link #buildEntityFlags(Player, boolean)} собирает полный снапшот.
     */
    private static final int ENTITY_FLAGS_INDEX = 0;
    private static final byte FLAG_ON_FIRE   = 0x01;
    private static final byte FLAG_CROUCHING = 0x02;
    // 0x04 — не используется в протоколе (был sprinting в 1.8, вырезан в 1.9)
    private static final byte FLAG_SPRINTING = 0x08;
    private static final byte FLAG_SWIMMING  = 0x10;
    private static final byte FLAG_INVISIBLE = 0x20;
    private static final byte FLAG_GLOWING   = 0x40;
    private static final byte FLAG_ELYTRA    = (byte) 0x80;

    private static final int RESYNC_TICKS = 200;

    /**
     * C12: флаг реальной инициализации PacketEvents. Устанавливается в
     * {@link #start()} только после успешной проверки, что и API, и
     * playerManager доступны. Раньше {@code isAvailable()} возвращал true
     * при частично загруженном PacketEvents, что давало спам warning'ов
     * в логе при каждом sendPacket.
     */
    private volatile boolean initialized = false;

    private final TownyWarPlugin plugin;
    private final TownyHook townyHook;
    private final WarManager warManager;

    private final Set<UUID> glowOn = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> currentTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, NamedTextColor>> sentColors = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Byte>> sentFlags = new ConcurrentHashMap<>();

    private BukkitTask ticker;
    private int ticksSinceResync = 0;

    public GlowManager(TownyWarPlugin plugin, TownyHook townyHook, WarManager warManager) {
        this.plugin = plugin;
        this.townyHook = townyHook;
        this.warManager = warManager;
    }

    /**
     * C12: помимо наличия API-ссылки проверяем, что API инициализирован
     * (playerManager доступен). Раньше isAvailable() возвращал true и при
     * частично загруженном PacketEvents, что давало спам warning'ов в логе
     * при каждом sendPacket.
     */
    public boolean isAvailable() {
        if (!initialized) return false;
        try {
            var api = PacketEvents.getAPI();
            return api != null && api.getPlayerManager() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public void start() {
        try {
            var api = PacketEvents.getAPI();
            if (api == null || api.getPlayerManager() == null) {
                plugin.getLogger().warning("PacketEvents API не инициализирован, подсветка отключена.");
                return;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("PacketEvents недоступен, подсветка отключена: " + t.getMessage());
            return;
        }
        initialized = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
        plugin.getLogger().info("Менеджер подсветки (PacketEvents, per-viewer) запущен.");
    }

    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        for (UUID viewerId : new HashSet<>(currentTargets.keySet())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) disableFor(viewer);
        }
        glowOn.clear();
        currentTargets.clear();
        sentColors.clear();
        sentFlags.clear();
    }

    public boolean isGlowEnabled(UUID id) { return glowOn.contains(id); }

    /**
     * C13: заменяем legacy §-секции на нормальный Component.
     */
    public void toggle(Player player, boolean on) {
        if (!isAvailable()) {
            player.sendMessage(Component.text("PacketEvents не установлен — свечение недоступно.",
                    NamedTextColor.RED));
            return;
        }
        UUID id = player.getUniqueId();
        if (on) {
            glowOn.add(id);
            sentColors.remove(id);
            sentFlags.remove(id);
            Bukkit.getScheduler().runTask(plugin, () -> forceUpdate(player));
        } else {
            glowOn.remove(id);
            disableFor(player);
        }
    }

    private void forceUpdate(Player viewer) {
        if (!viewer.isOnline()) return;
        var town = townyHook.getTownOf(viewer);
        if (town == null) return;
        War war = warManager.getWar(town.getName());
        if (war == null || war.getPhase() != WarPhase.COMBAT) return;
        updateViewer(viewer, war, town.getName());
    }

    private void tick() {
        if (!isAvailable()) return;

        if (++ticksSinceResync >= RESYNC_TICKS) {
            ticksSinceResync = 0;
            sentFlags.clear();
        }

        for (UUID id : new ArrayList<>(glowOn)) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer == null) {
                glowOn.remove(id);
                currentTargets.remove(id);
                sentColors.remove(id);
                sentFlags.remove(id);
                continue;
            }
            var town = townyHook.getTownOf(viewer);
            War war = town == null ? null : warManager.getWar(town.getName());
            if (war == null || war.getPhase() != WarPhase.COMBAT) {
                if (currentTargets.containsKey(id)) disableFor(viewer);
                continue;
            }
            updateViewer(viewer, war, town.getName());
        }
    }

    /**
     * B9: фильтр по радиусу и миру. Раньше подсветка слалась на всех
     * воюющих в мире без дистанции — при 100+ онлайн это тысячи пар
     * (viewer, target) и всплеск пакетов. Теперь — только те, кто реально
     * видим в радиусе.
     */
    private void updateViewer(Player viewer, War war, String viewerTownName) {
        UUID viewerId = viewer.getUniqueId();
        Set<UUID> newTargets = new HashSet<>();

        double radius = plugin.getConfigManager().getGlowRadius();
        double radiusSq = radius * radius;
        Location viewerLoc = viewer.getLocation();

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(viewer)) continue;
            var otherTown = townyHook.getTownOf(other);
            if (otherTown == null) continue;
            if (!war.involves(otherTown.getName())) continue;

            War otherWar = warManager.getWar(otherTown.getName());
            if (otherWar == null || otherWar.getPhase() != WarPhase.COMBAT) continue;

            Location otherLoc = other.getLocation();
            if (viewerLoc.getWorld() == null || viewerLoc.getWorld() != otherLoc.getWorld()) continue;
            if (viewerLoc.distanceSquared(otherLoc) > radiusSq) continue;

            boolean own = otherTown.getName().equalsIgnoreCase(viewerTownName);
            NamedTextColor color = own ? OWN_COLOR : ENEMY_COLOR;

            sendTeamPacket(viewer, other, color);
            sendGlowPacket(viewer, other, true);

            newTargets.add(other.getUniqueId());
        }

        Set<UUID> oldTargets = currentTargets.getOrDefault(viewerId, Set.of());
        for (UUID oldId : oldTargets) {
            if (newTargets.contains(oldId)) continue;
            Player oldP = Bukkit.getPlayer(oldId);
            if (oldP != null) {
                removeTeamPacket(viewer, oldP);
                sendGlowPacket(viewer, oldP, false);
            }
        }
        currentTargets.put(viewerId, newTargets);
    }

    private void sendGlowPacket(Player viewer, Player target, boolean glowing) {
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = target.getUniqueId();

        byte flags = buildEntityFlags(target, glowing);

        Map<UUID, Byte> perViewer = sentFlags
                .computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>());
        Byte prev = perViewer.get(targetId);
        // B6: сравнение через byteValue — Byte==Byte хрупко (работает
        // только из-за кэша -128..127). Явное сравнение по значению.
        if (prev != null && prev.byteValue() == flags) return;

        perViewer.put(targetId, flags);

        try {
            EntityData data = new EntityData(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, flags);
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(
                    target.getEntityId(), List.of(data));
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
        } catch (Throwable t) {
            if (prev == null) perViewer.remove(targetId);
            else perViewer.put(targetId, prev);
            plugin.getLogger().log(Level.WARNING,
                    "sendGlowPacket fail: " + target.getName() + " -> " + viewer.getName(), t);
        }
    }

    /**
     * Собирает полный снапшот byte entity flags для target'а.
     * <p>
     * Клиент трактует этот байт как ПОЛНУЮ замену состояния флагов, а не
     * как побитовый OR к предыдущему. Поэтому обязательно выставляем ВСЕ
     * биты, соответствующие текущему состоянию игрока, а не только
     * «glow». Иначе клиент может залипнуть в неверном рендере
     * (в частности, в невидимости) до следующего полного обновления.
     * <p>
     * {@code sprinting} критичен: без него клиент при получении снапшота,
     * где бит снят, может «застрять» в неправильном состоянии скелета
     * бегущего игрока.
     */
    private byte buildEntityFlags(Player target, boolean glowing) {
        byte flags = 0;
        if (target.getFireTicks() > 0)  flags |= FLAG_ON_FIRE;
        if (target.isSneaking())        flags |= FLAG_CROUCHING;
        if (target.isSprinting())       flags |= FLAG_SPRINTING;
        if (target.isSwimming())        flags |= FLAG_SWIMMING;
        if (target.isInvisible())       flags |= FLAG_INVISIBLE;
        if (glowing)                    flags |= FLAG_GLOWING;
        if (target.isGliding())         flags |= FLAG_ELYTRA;
        return flags;
    }

    /**
     * B8: TeamMode выбирается по prev. При первой паре (viewer, target) —
     * CREATE, при смене цвета — UPDATE. Раньше всегда шёл CREATE, что
     * при повторной отправке с другим цветом могло дать ошибку/замену.
     */
    private void sendTeamPacket(Player viewer, Player target, NamedTextColor color) {
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = target.getUniqueId();

        Map<UUID, NamedTextColor> perViewer = sentColors
                .computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>());
        NamedTextColor prev = perViewer.get(targetId);
        if (prev == color) return;

        perViewer.put(targetId, color);

        String teamName = teamName(target);

        Component prefix = Component.empty();
        Component suffix = Component.empty();
        try {
            Team bukkitTeam = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(target.getName());
            if (bukkitTeam != null) {
                String p = bukkitTeam.getPrefix();
                String s = bukkitTeam.getSuffix();
                if (p != null && !p.isEmpty()) prefix = LegacyComponentSerializer.legacySection().deserialize(p);
                if (s != null && !s.isEmpty()) suffix = LegacyComponentSerializer.legacySection().deserialize(s);
            }
        } catch (Throwable ignored) { }

        TeamMode mode = (prev == null) ? TeamMode.CREATE : TeamMode.UPDATE;

        try {
            ScoreBoardTeamInfo teamInfo = new ScoreBoardTeamInfo(
                    Component.empty(),
                    prefix,
                    suffix,
                    NameTagVisibility.ALWAYS,
                    CollisionRule.ALWAYS,
                    color,
                    OptionData.NONE
            );
            WrapperPlayServerTeams packet = new WrapperPlayServerTeams(
                    teamName, mode, teamInfo, List.of(target.getName()));
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
        } catch (Exception e) {
            if (prev == null) perViewer.remove(targetId);
            else perViewer.put(targetId, prev);
            plugin.getLogger().log(Level.WARNING,
                    "sendTeamPacket: " + target.getName() + " -> " + viewer.getName(), e);
        }
    }

    /**
     * B7: восстановление состояния команды у viewer'а.
     * <p>
     * Раньше здесь был REMOVE + ADD_ENTITIES в bukkit-team, но при
     * отсутствии bukkit-team команда не восстанавливалась — ник target'а
     * мог потерять цвет до следующего пакета от сервера.
     * <p>
     * Теперь вместо REMOVE всегда UPDATE с реальным состоянием
     * bukkit-team (prefix/suffix/color) или дефолтным пустым/белым,
     * если её нет. Команда остаётся у клиента, но в нейтральном
     * состоянии — так надёжнее.
     */
    private void removeTeamPacket(Player viewer, Player target) {
        UUID viewerId = viewer.getUniqueId();
        UUID targetId = target.getUniqueId();

        Map<UUID, NamedTextColor> perViewerColors = sentColors.get(viewerId);
        if (perViewerColors != null) perViewerColors.remove(targetId);

        String teamName = teamName(target);

        Component prefix = Component.empty();
        Component suffix = Component.empty();
        NamedTextColor color = NamedTextColor.WHITE;
        try {
            Team bukkitTeam = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(target.getName());
            if (bukkitTeam != null) {
                String p = bukkitTeam.getPrefix();
                String s = bukkitTeam.getSuffix();
                if (p != null && !p.isEmpty()) prefix = LegacyComponentSerializer.legacySection().deserialize(p);
                if (s != null && !s.isEmpty()) suffix = LegacyComponentSerializer.legacySection().deserialize(s);
                try {
                    NamedTextColor c = NamedTextColor.NAMES.value(
                            bukkitTeam.getColor().name().toLowerCase(Locale.ROOT));
                    if (c != null) color = c;
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }

        try {
            ScoreBoardTeamInfo info = new ScoreBoardTeamInfo(
                    Component.empty(),
                    prefix,
                    suffix,
                    NameTagVisibility.ALWAYS,
                    CollisionRule.ALWAYS,
                    color,
                    OptionData.NONE
            );
            WrapperPlayServerTeams restore = new WrapperPlayServerTeams(
                    teamName, TeamMode.UPDATE, info, List.of(target.getName()));
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, restore);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "removeTeamPacket: " + target.getName() + " -> " + viewer.getName(), e);
        }
    }

    private static String teamName(Player target) {
        return TEAM_PREFIX + target.getUniqueId().toString().substring(0, 8);
    }

    private void disableFor(Player viewer) {
        UUID id = viewer.getUniqueId();
        Set<UUID> targets = currentTargets.remove(id);
        if (targets == null) {
            sentColors.remove(id);
            sentFlags.remove(id);
            return;
        }
        for (UUID tid : targets) {
            Player t = Bukkit.getPlayer(tid);
            if (t != null) {
                removeTeamPacket(viewer, t);
                sendGlowPacket(viewer, t, false);
            }
        }
        sentColors.remove(id);
        sentFlags.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        sentFlags.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        sentFlags.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        sentFlags.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        glowOn.remove(id);
        currentTargets.remove(id);
        sentColors.remove(id);
        sentFlags.remove(id);
    }
}