package ru.example.townywar.flag;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.config.ConfigManager;
import ru.example.townywar.war.CombatWindowManager;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarManager;
import ru.example.townywar.war.WarPhase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FlagManager implements CombatWindowManager.WindowHook {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final BlockFace[] ROTATION_FACES = {
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST
    };

    private final TownyWarPlugin plugin;
    private final ConfigManager config;
    private final WarManager warManager;
    private final CombatWindowManager windowManager;
    private final FlagRepository repo;
    private final FlagDisplay display;

    private final Map<UUID, PlacedFlag> flags = new ConcurrentHashMap<>();
    private final Map<String, UUID> blockIndex = new ConcurrentHashMap<>();
    private final Map<String, UUID> chunkIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Material> originalMaterials = new ConcurrentHashMap<>();

    private BukkitTask ticker;

    public FlagManager(TownyWarPlugin plugin, ConfigManager config, WarManager warManager,
                       CombatWindowManager windowManager, FlagRepository repo) {
        this.plugin = plugin; this.config = config; this.warManager = warManager;
        this.windowManager = windowManager; this.repo = repo;
        this.display = new FlagDisplay(plugin);
    }

    public void start() {
        cleanupOrphanDisplays();
        loadFromDatabase();
        windowManager.registerHook(this);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /**
     * C2: shutdown-путь.
     */
    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        long now = System.currentTimeMillis();
        for (PlacedFlag f : flags.values()) {
            if (!f.isPaused() && f.getCaptureFinishAt() != null) {
                int remaining = (int) Math.max(0, (f.getCaptureFinishAt() - now) / 1000L);
                f.setPausedRemainingSeconds(remaining);
                f.setCaptureFinishAt(null);
            }
            display.remove(f);
        }
        repo.flushAndShutdown(new ArrayList<>(flags.values()));
    }

    public void spawnDisplaysInChunk(Chunk chunk) {
        if (chunk == null) return;
        String ck = chunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        UUID flagId = chunkIndex.get(ck);
        if (flagId == null) return;
        PlacedFlag f = flags.get(flagId);
        if (f == null) return;
        display.spawn(f);
    }

    /**
     * A1/A8: снимает все флаги указанной войны — визуал, индексы, БД.
     */
    public void removeAllByWar(long warId) {
        List<PlacedFlag> toRemove = new ArrayList<>();
        for (PlacedFlag f : flags.values()) {
            if (f.getWarId() == warId) toRemove.add(f);
        }
        for (PlacedFlag f : toRemove) {
            removeFlag(f, false);
        }
    }

    private void cleanupOrphanDisplays() {
        NamespacedKey key = display.getDisplayKey();
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            if (!plugin.getTownyHook().api().isTownyWorld(w)) continue;
            for (Chunk c : w.getLoadedChunks()) {
                for (Entity e : c.getEntities()) {
                    if (e instanceof TextDisplay td && td.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                        td.remove(); removed++;
                    }
                }
            }
        }
        if (removed > 0) plugin.getLogger().info("Убрано осиротевших дисплеев: " + removed);
    }

    private void loadFromDatabase() {
        long now = System.currentTimeMillis();
        boolean windowActive = windowManager.isCombatWindowActive();

        for (PlacedFlag f : repo.loadAll()) {
            War flagWar = warManager.getWarById(f.getWarId());
            if (flagWar == null || !flagWar.isActive() || flagWar.getPhase() != WarPhase.COMBAT) {
                plugin.getLogger().warning("Флаг " + f.getFlagId() + " ссылается на войну #"
                        + f.getWarId() + " не в фазе COMBAT — удаляем.");
                repo.delete(f);
                continue;
            }
            String opponent = flagWar.opposing(f.getOwnerTown());
            if (opponent == null || !f.getTargetTown().equalsIgnoreCase(opponent)) {
                plugin.getLogger().warning("Флаг " + f.getFlagId() + " поставлен не по войне ("
                        + f.getOwnerTown() + " → " + f.getTargetTown()
                        + ", ожидалось → " + opponent + ") — удаляем.");
                repo.delete(f);
                continue;
            }

            World world = Bukkit.getWorld(f.getWorld());
            int cx = f.getX() >> 4, cz = f.getZ() >> 4;
            flags.put(f.getFlagId(), f);
            blockIndex.put(blockKey(f.getWorld(), f.getX(), f.getY(), f.getZ()), f.getFlagId());
            chunkIndex.put(chunkKey(f.getWorld(), cx, cz), f.getFlagId());

            if (world != null) {
                Block block = world.getBlockAt(f.getX(), f.getY(), f.getZ());
                Material want = f.getType() == FlagType.CAPTURE ? Material.RED_BANNER : Material.GREEN_BANNER;
                if (block.getType() != want) block.setType(want, false);
                originalMaterials.putIfAbsent(f.getFlagId(), Material.AIR);
            }

            // Truce-aware: если по войне активно перемирие — флаг не бежит,
            // даже когда окно открыто. Проверка обязательна ДО разморозки.
            boolean truceActive = plugin.getWarshopManager() != null
                    && plugin.getWarshopManager().isTruceActive(f.getWarId());
            boolean canRun = windowActive && !truceActive;

            if (f.isPaused()) {
                if (canRun) {
                    Integer remaining = f.getPausedRemainingSeconds();
                    if (remaining == null) remaining = 0;
                    f.setCaptureFinishAt(now + remaining * 1000L);
                    f.setPausedRemainingSeconds(null);
                    repo.update(f);
                }
            } else if (f.getCaptureFinishAt() != null) {
                if (f.getCaptureFinishAt() <= now) {
                    if (canRun) {
                        if (f.getType() == FlagType.CAPTURE) captureChunk(f); else surrenderWar(f);
                        removeFlag(f, false);
                        continue;
                    } else {
                        // Время вышло, но бежать нельзя — фиксируем remaining=0 и паркуем.
                        f.setPausedRemainingSeconds(0);
                        f.setCaptureFinishAt(null);
                        repo.update(f);
                    }
                } else if (!canRun) {
                    int remaining = (int) Math.max(0, (f.getCaptureFinishAt() - now) / 1000L);
                    f.setPausedRemainingSeconds(remaining);
                    f.setCaptureFinishAt(null);
                    repo.update(f);
                }
            }

            display.spawn(f);
        }
        plugin.getLogger().info("Загружено флагов из БД: " + flags.size());
    }

    /**
     * Хук окна: при открытии размораживаем флаги, кроме тех, что под truce.
     * Основную паузу/разморозку делает tick — здесь только ускоряем
     * реакцию на transition «окно закрыто → открыто», чтобы таймеры
     * не ждали следующего тика.
     */
    @Override public void onWindowOpen() {
        long now = System.currentTimeMillis();
        for (PlacedFlag f : flags.values()) {
            if (!f.isPaused()) continue;

            War w = warManager.getWarById(f.getWarId());
            if (w != null && plugin.getWarshopManager() != null
                    && plugin.getWarshopManager().isTruceActive(w.getId())) {
                continue;
            }

            Integer remaining = f.getPausedRemainingSeconds();
            if (remaining == null) remaining = 0;
            f.setCaptureFinishAt(now + remaining * 1000L);
            f.setPausedRemainingSeconds(null);
            repo.update(f); display.update(f);
        }
    }

    @Override public void onWindowClose() {
        long now = System.currentTimeMillis();
        for (PlacedFlag f : flags.values()) {
            if (f.isPaused()) continue;
            Long finish = f.getCaptureFinishAt();
            int remaining = finish == null ? 0 : (int) Math.max(0, (finish - now) / 1000L);
            f.setPausedRemainingSeconds(remaining);
            f.setCaptureFinishAt(null);
            repo.update(f); display.update(f);
        }
    }

    public boolean place(Player player, Block block, FlagType type) {
        if (!windowManager.isCombatWindowActive()) {
            player.sendActionBar(MM.deserialize("<red>Флаги можно ставить только во время боевого окна.</red>"));
            return false;
        }
        int y = block.getY();
        if (y < config.getCaptureMinY() || y > config.getCaptureMaxY()) {
            player.sendActionBar(MM.deserialize("<red>Флаг можно ставить только в пределах Y " + config.getCaptureMinY() + "–" + config.getCaptureMaxY() + ".</red>"));
            return false;
        }
        Location loc = block.getLocation();
        World world = loc.getWorld();
        if (world == null) return false;

        if (block.getType() == Material.RED_BANNER || block.getType() == Material.GREEN_BANNER) {
            player.sendActionBar(MM.deserialize("<red>Здесь уже стоит флаг.</red>"));
            return false;
        }
        if (!block.getType().isAir() && block.getType() != Material.SHORT_GRASS && block.getType() != Material.TALL_GRASS) {
            player.sendActionBar(MM.deserialize("<red>Здесь нельзя поставить флаг — блок занят.</red>"));
            return false;
        }

        Chunk chunk = loc.getChunk();
        int chunkX = chunk.getX(), chunkZ = chunk.getZ();

        String ck = chunkKey(world.getName(), chunkX, chunkZ);
        if (chunkIndex.containsKey(ck)) {
            player.sendActionBar(MM.deserialize("<red>В этом чанке уже стоит флаг. Дождитесь его уничтожения.</red>"));
            return false;
        }
        if (blockIndex.containsKey(blockKey(world.getName(), block.getX(), y, block.getZ()))) {
            player.sendActionBar(MM.deserialize("<red>Здесь уже стоит флаг.</red>"));
            return false;
        }

        Town ownTown = plugin.getTownyHook().getTownOf(player);
        if (ownTown == null) return false;

        War war = warManager.getWar(ownTown.getName());
        if (war == null || war.getPhase() != WarPhase.COMBAT) {
            player.sendActionBar(MM.deserialize("<red>Ваш город не участвует в активной войне в фазе боя.</red>"));
            return false;
        }

        // Перемирие: флаги ставить нельзя.
        if (plugin.getWarshopManager() != null
                && plugin.getWarshopManager().isTruceActive(war.getId())) {
            player.sendActionBar(MM.deserialize("<red>Во время перемирия флаги ставить нельзя.</red>"));
            return false;
        }

        TownBlock tb = plugin.getTownyHook().api().getTownBlock(loc);
        if (tb == null || !tb.hasTown()) {
            player.sendActionBar(MM.deserialize("<red>Этот чанк никому не принадлежит.</red>"));
            return false;
        }
        Town targetTown = tb.getTownOrNull();
        if (targetTown == null) return false;
        if (targetTown.getName().equalsIgnoreCase(ownTown.getName())) {
            player.sendActionBar(MM.deserialize("<red>Нельзя ставить флаг в своём городе.</red>"));
            return false;
        }

        String opponentName = war.opposing(ownTown.getName());
        if (opponentName == null || !targetTown.getName().equalsIgnoreCase(opponentName)) {
            player.sendActionBar(MM.deserialize(
                    "<red>Флаги можно ставить только в чанках города-противника по вашей войне.</red>"));
            return false;
        }

        TownBlock home = null;
        try { home = targetTown.getHomeBlockOrNull(); } catch (Throwable ignored) { }
        boolean isHomeChunk = home != null && home.equals(tb);

        if (type == FlagType.CAPTURE) {
            if (isHomeChunk) {
                player.sendActionBar(MM.deserialize("<red>Нельзя захватить центральный чанк — используйте Флаг Капитуляции.</red>"));
                return false;
            }
            if (!isAdjacentToOwnTerritory(loc, ownTown)) {
                player.sendActionBar(MM.deserialize("<red>Флаг нужно ставить в чанке, смежном с вашей территорией.</red>"));
                return false;
            }
        } else if (type == FlagType.SURRENDER) {
            if (!isHomeChunk) {
                player.sendActionBar(MM.deserialize("<red>Флаг Капитуляции ставится только в центральном чанке противника.</red>"));
                return false;
            }
        }

        if (type == FlagType.CAPTURE) {
            int max = warManager.getMaxConcurrentFlags(ownTown.getName());
            long current = flags.values().stream()
                    .filter(f -> f.getType() == FlagType.CAPTURE)
                    .filter(f -> f.getOwnerTown().equalsIgnoreCase(ownTown.getName())).count();
            if (current >= max) {
                player.sendActionBar(MM.deserialize("<red>У вас уже установлено максимум флагов захвата (" + max + ").</red>"));
                return false;
            }
        } else {
            long current = flags.values().stream()
                    .filter(f -> f.getType() == FlagType.SURRENDER)
                    .filter(f -> f.getOwnerTown().equalsIgnoreCase(ownTown.getName())).count();
            if (current >= 1) {
                player.sendActionBar(MM.deserialize("<red>У вас уже есть активный Флаг Капитуляции.</red>"));
                return false;
            }
        }

        UUID flagId = UUID.randomUUID();
        int durability = type == FlagType.CAPTURE ? config.getCaptureDurability() : config.getSurrenderDurability();
        int seconds = type == FlagType.CAPTURE ? config.getCaptureSeconds() : config.getSurrenderSeconds();
        long now = System.currentTimeMillis();

        Material original = block.getType();
        Material bannerMat = type == FlagType.CAPTURE ? Material.RED_BANNER : Material.GREEN_BANNER;
        block.setType(bannerMat, false);

        BlockData bd = block.getBlockData();
        if (bd instanceof Rotatable rot) {
            BlockFace looking = yawToFace(player.getLocation().getYaw());
            rot.setRotation(looking.getOppositeFace());
            block.setBlockData(bd, false);
        }

        PlacedFlag flag = new PlacedFlag(flagId, war.getId(), type,
                ownTown.getName(), player.getName(), targetTown.getName(),
                world.getName(), block.getX(), y, block.getZ(), chunkX, chunkZ,
                durability, durability, now, now + seconds * 1000L, null);

        flags.put(flagId, flag);
        blockIndex.put(blockKey(world.getName(), block.getX(), y, block.getZ()), flagId);
        chunkIndex.put(ck, flagId);
        originalMaterials.put(flagId, original);
        repo.insert(flag);

        display.spawn(flag);
        display.playPlaceEffect(block.getLocation());

        plugin.getWarLogRepository().log(war.getId(), player.getName(), "FLAG_PLACED",
                "type=" + type + ";town=" + targetTown.getName() + ";x=" + block.getX() + ";y=" + y + ";z=" + block.getZ());

        notifySides(flag, targetTown);
        return true;
    }

    private void notifySides(PlacedFlag flag, Town targetTown) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            Town t = plugin.getTownyHook().getTownOf(online);
            if (t == null) continue;
            if (t.getName().equalsIgnoreCase(flag.getOwnerTown())) {
                online.sendMessage(MM.deserialize("<gold>Вы установили флаг. Защищайте его!</gold>"));
            } else if (t.getName().equalsIgnoreCase(targetTown.getName())) {
                online.sendMessage(MM.deserialize("<red>Защитите флаг! Противник начал захват в чанке <white>"
                        + flag.getX() + " " + flag.getY() + " " + flag.getZ() + "</white>.</red>"));
            }
        }
    }

    private void notifyFlagBroken(PlacedFlag flag) {
        Town ownerTown = plugin.getTownyHook().getTown(flag.getOwnerTown());
        Town targetTown = plugin.getTownyHook().getTown(flag.getTargetTown());

        String ownerMsg = "<red>Ваш флаг уничтожен! Захват прерван в чанке <white>"
                + flag.getX() + " " + flag.getY() + " " + flag.getZ() + "</white>.</red>";
        String targetMsg = "<green>Вы успешно защитили чанк <white>"
                + flag.getX() + " " + flag.getY() + " " + flag.getZ() + "</white>!</green>";

        if (ownerTown != null) {
            var msg = MM.deserialize(ownerMsg);
            for (Resident r : ownerTown.getResidents()) {
                Player p = r.getPlayer(); if (p != null) p.sendMessage(msg);
            }
        }
        if (targetTown != null) {
            var msg = MM.deserialize(targetMsg);
            for (Resident r : targetTown.getResidents()) {
                Player p = r.getPlayer(); if (p != null) p.sendMessage(msg);
            }
        }
    }

    /**
     * A1 + Warshop: каждый тик проверяем, что война флага всё ещё в COMBAT.
     * Если война пропала или фаза сменилась — флаг снимаем.
     * <p>
     * Флаги «бегут» только когда открыто боевое окно И нет перемирия
     * ({@code shouldRun}). Пауза и разморозка симметричны: при потере
     * любого из условий флаг паркуется с сохранением remaining, при
     * возврате обоих — снимается с паузы.
     */
    private void tick() {
        long now = System.currentTimeMillis();
        boolean windowActive = windowManager.isCombatWindowActive();

        for (PlacedFlag f : new ArrayList<>(flags.values())) {
            War war = warManager.getWarById(f.getWarId());
            if (war == null || !war.isActive() || war.getPhase() != WarPhase.COMBAT) {
                removeFlag(f, false);
                continue;
            }

            // Восстановление баннера, если он был снесён (взрывом, чужим
            // плагином, /setblock). Иначе флаг «висит в воздухе», а сломать
            // его нельзя — BlockBreakEvent не увидит блок баннера.
            World w = Bukkit.getWorld(f.getWorld());
            if (w != null && w.isChunkLoaded(f.getX() >> 4, f.getZ() >> 4)) {
                Block cur = w.getBlockAt(f.getX(), f.getY(), f.getZ());
                Material bannerMat = f.getType() == FlagType.CAPTURE
                        ? Material.RED_BANNER : Material.GREEN_BANNER;
                if (cur.getType() != bannerMat) {
                    cur.setType(bannerMat, false);
                }
            }

            boolean truceActive = plugin.getWarshopManager() != null
                    && plugin.getWarshopManager().isTruceActive(war.getId());

            // Флаг бежит только если открыто окно И нет перемирия.
            boolean shouldRun = windowActive && !truceActive;

            if (f.isPaused()) {
                if (!shouldRun) continue;
                Integer saved = f.getPausedRemainingSeconds();
                if (saved == null) saved = 0;
                f.setCaptureFinishAt(now + saved * 1000L);
                f.setPausedRemainingSeconds(null);
                repo.update(f); display.update(f);
                continue;
            }

            // Флаг бежит — если должен быть на паузе, ставим.
            if (!shouldRun) {
                int remaining = (int) Math.max(0, (f.getCaptureFinishAt() - now) / 1000L);
                f.setPausedRemainingSeconds(remaining);
                f.setCaptureFinishAt(null);
                repo.update(f); display.update(f);
                continue;
            }

            if (f.getCaptureFinishAt() != null && now >= f.getCaptureFinishAt()) {
                if (f.getType() == FlagType.CAPTURE) captureChunk(f); else surrenderWar(f);
                removeFlag(f, false);
                continue;
            }
            display.update(f);
        }
    }

    /**
     * A4 + Warshop: может ли игрок вообще атаковать флаг.
     * Требуется одновременно: открытое боевое окно, фаза COMBAT, нет
     * активного перемирия, игрок — житель одного из двух воюющих городов.
     * Нейтрал не проходит. Обе воюющие стороны могут ломать.
     */
    public boolean canPlayerBreakFlag(Player player, PlacedFlag f) {
        if (player == null || f == null) return false;
        if (!windowManager.isCombatWindowActive()) return false;
        War war = warManager.getWarById(f.getWarId());
        if (war == null || !war.isActive() || war.getPhase() != WarPhase.COMBAT) return false;

        // Перемирие: флаг неприкосновенен.
        if (plugin.getWarshopManager() != null
                && plugin.getWarshopManager().isTruceActive(war.getId())) return false;

        Town playerTown = plugin.getTownyHook().getTownOf(player);
        if (playerTown == null) return false;
        return war.involves(playerTown.getName());
    }

    public boolean handleBreak(Player player, Block block) {
        String key = blockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        UUID flagId = blockIndex.get(key);
        if (flagId == null) return false;
        PlacedFlag f = flags.get(flagId);
        if (f == null) return false;

        // A4 + Warshop: defense in depth — даже если FlagListener пропустил, здесь режем.
        if (!canPlayerBreakFlag(player, f)) return false;

        int newDur = f.getDurability() - 1;
        if (newDur <= 0) {
            display.playBreakEffect(block.getLocation());
            notifyFlagBroken(f);
            removeFlag(f, true);
            player.sendActionBar(MM.deserialize("<red>Флаг уничтожен!</red>"));

            // В статистику идёт только слом вражеского флага.
            // Снос своего — разрешён кодом, но не считается «вкладом».
            Town breakerTown = plugin.getTownyHook().getTownOf(player);
            boolean enemyFlag = breakerTown != null
                    && !breakerTown.getName().equalsIgnoreCase(f.getOwnerTown());
            plugin.getWarLogRepository().log(f.getWarId(), player.getName(),
                    enemyFlag ? "FLAG_BROKEN_ENEMY" : "FLAG_BROKEN_OWN",
                    "type=" + f.getType() + ";owner=" + f.getOwnerTown()
                            + ";x=" + f.getX() + ";y=" + f.getY() + ";z=" + f.getZ());
            return true;
        }

        f.setDurability(newDur);
        repo.update(f); display.update(f);
        display.playHitEffect(block.getLocation());
        player.sendActionBar(MM.deserialize("<yellow>Прочность флага: <white>" + newDur + "/" + f.getMaxDurability() + "</white></yellow>"));
        return false;
    }

    public boolean isFlagBlock(Block block) {
        return blockIndex.containsKey(blockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    }

    public void removeFlag(PlacedFlag f, boolean dropItem) {
        flags.remove(f.getFlagId());
        blockIndex.remove(blockKey(f.getWorld(), f.getX(), f.getY(), f.getZ()));
        chunkIndex.remove(chunkKey(f.getWorld(), f.getX() >> 4, f.getZ() >> 4), f.getFlagId());
        display.remove(f);
        repo.delete(f);

        World world = Bukkit.getWorld(f.getWorld());
        if (world == null) return;

        Block block = world.getBlockAt(f.getX(), f.getY(), f.getZ());
        Material bannerMat = f.getType() == FlagType.CAPTURE ? Material.RED_BANNER : Material.GREEN_BANNER;

        if (block.getType() == bannerMat) {
            Material original = originalMaterials.remove(f.getFlagId());
            block.setType(original != null ? original : Material.AIR, false);
        } else {
            originalMaterials.remove(f.getFlagId());
        }

        if (dropItem) {
            Location loc = new Location(world, f.getX() + 0.5, f.getY() + 0.5, f.getZ() + 0.5);
            world.dropItemNaturally(loc, FlagItemFactory.create(plugin, f.getType()));
        }
    }

    private boolean transferTownBlock(TownBlock tb, Town from, Town to) {
        if (tb == null || to == null) return false;
        try {
            if (from != null && from.hasTownBlock(tb)) {
                from.removeTownBlock(tb);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[transfer] removeTownBlock fail: " + t.getMessage());
        }
        try {
            tb.setTown(to);
        } catch (Throwable t) {
            plugin.getLogger().warning("[transfer] setTown fail: " + t.getMessage());
            return false;
        }
        try {
            if (!to.hasTownBlock(tb)) {
                to.addTownBlock(tb);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[transfer] addTownBlock fail: " + t.getMessage());
        }
        try {
            TownyAPI.getInstance().getDataSource().saveTownBlock(tb);
        } catch (Throwable t) {
            plugin.getLogger().warning("[transfer] saveTownBlock fail: " + t.getMessage());
        }
        try {
            if (from != null) TownyAPI.getInstance().getDataSource().saveTown(from);
        } catch (Throwable ignored) { }
        try {
            TownyAPI.getInstance().getDataSource().saveTown(to);
        } catch (Throwable ignored) { }
        return true;
    }

    /**
     * A1: защитная проверка фазы. Вызывается из tick, но на случай
     * будущих точек вызова — валидируем ещё раз.
     */
    private void captureChunk(PlacedFlag f) {
        War war = warManager.getWarById(f.getWarId());
        if (war == null || war.getPhase() != WarPhase.COMBAT) return;

        World world = Bukkit.getWorld(f.getWorld());
        if (world == null) return;
        Location loc = new Location(world, f.getX(), f.getY(), f.getZ());
        TownBlock tb = plugin.getTownyHook().api().getTownBlock(loc);
        if (tb == null || !tb.hasTown()) return;

        Town attacker = plugin.getTownyHook().getTown(f.getOwnerTown());
        Town defender = plugin.getTownyHook().getTown(f.getTargetTown());
        if (attacker == null || defender == null) return;

        boolean ok = transferTownBlock(tb, defender, attacker);
        if (ok) {
            plugin.getLogger().info("Чанк " + f.getChunkX() + "," + f.getChunkZ()
                    + " перешёл от " + defender.getName() + " к " + attacker.getName());
            plugin.getWarLogRepository().log(f.getWarId(), null, "CHUNK_CAPTURED",
                    "chunk=" + f.getChunkX() + "," + f.getChunkZ() + ";from=" + defender.getName() + ";to=" + attacker.getName());
            if (f.getPlacedBy() != null) {
                plugin.getWarLogRepository().log(f.getWarId(), f.getPlacedBy(), "FLAG_COMPLETED",
                        "type=CAPTURE;x=" + f.getX() + ";y=" + f.getY() + ";z=" + f.getZ()
                                + ";chunk=" + f.getChunkX() + "," + f.getChunkZ());
            }
        } else {
            plugin.getWarLogRepository().log(f.getWarId(), null, "CHUNK_CAPTURE_FAILED",
                    "chunk=" + f.getChunkX() + "," + f.getChunkZ());
        }

        display.playCaptureEffect(loc);
    }

    /**
     * A2: победитель — сторона, установившая флаг капитуляции.
     */
    private void surrenderWar(PlacedFlag f) {
        War war = warManager.getWarById(f.getWarId());
        if (war == null || war.getPhase() != WarPhase.COMBAT) return;
        String winner = f.getOwnerTown().equalsIgnoreCase(war.getAttackerTown())
                ? war.getAttackerTown() : war.getDefenderTown();
        warManager.startNegotiation(war, winner);
        plugin.getWarLogRepository().log(war.getId(), null, "SURRENDER_FLAG_COMPLETE", "winner=" + winner);
        if (f.getPlacedBy() != null) {
            plugin.getWarLogRepository().log(war.getId(), f.getPlacedBy(), "FLAG_COMPLETED",
                    "type=SURRENDER;winner=" + winner);
        }
    }

    private static String blockKey(String world, int x, int y, int z) { return world + ":" + x + ":" + y + ":" + z; }
    private static String chunkKey(String world, int cx, int cz) { return world + ":" + cx + ":" + cz; }

    private static BlockFace yawToFace(float yaw) {
        float y = ((yaw % 360f) + 360f) % 360f;
        int idx = (int) Math.floor((y * 16.0f / 360.0f) + 0.5) & 0xF;
        return ROTATION_FACES[idx];
    }

    private boolean isAdjacentToOwnTerritory(Location loc, Town ownTown) {
        World world = loc.getWorld();
        if (world == null) return false;
        Chunk center = loc.getChunk();
        int cx = center.getX(), cz = center.getZ();
        int y = loc.getBlockY();
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        for (int i = 0; i < 4; i++) {
            int nx = (cx + dx[i]) << 4;
            int nz = (cz + dz[i]) << 4;
            Location neighborLoc = new Location(world, nx + 8, y, nz + 8);
            TownBlock nb = plugin.getTownyHook().api().getTownBlock(neighborLoc);
            if (nb == null || !nb.hasTown()) continue;
            Town nbTown = nb.getTownOrNull();
            if (nbTown == null) continue;
            if (nbTown.getName().equalsIgnoreCase(ownTown.getName())) return true;
        }
        return false;
    }

    public PlacedFlag getFlagAt(Block block) {
        String key = blockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        UUID id = blockIndex.get(key);
        return id == null ? null : flags.get(id);
    }

    public Collection<PlacedFlag> allFlags() { return Collections.unmodifiableCollection(flags.values()); }
}