package ru.example.townywar.assault;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.config.ConfigManager;
import ru.example.townywar.flag.FlagManager;
import ru.example.townywar.war.CombatWindowManager;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarManager;
import ru.example.townywar.war.WarPhase;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AssaultTntManager implements CombatWindowManager.WindowHook {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final float BLAST_RESISTANCE_THRESHOLD = 100f;

    private final TownyWarPlugin plugin;
    private final ConfigManager config;
    private final WarManager warManager;
    private final CombatWindowManager windowManager;
    private final AssaultTntRepository repo;
    private final AssaultTntDisplay display;

    private final Map<UUID, AssaultTnt> tnts = new ConcurrentHashMap<>();
    private final Map<String, UUID> blockIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> chunkIndex = new ConcurrentHashMap<>();

    private BukkitTask ticker;

    public AssaultTntManager(TownyWarPlugin plugin, ConfigManager config, WarManager warManager,
                             CombatWindowManager windowManager, AssaultTntRepository repo) {
        this.plugin = plugin; this.config = config; this.warManager = warManager;
        this.windowManager = windowManager; this.repo = repo;
        this.display = new AssaultTntDisplay(plugin);
    }

    public void start() {
        cleanupOrphanDisplays();
        loadFromDatabase();
        windowManager.registerHook(this);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        long now = System.currentTimeMillis();
        for (AssaultTnt t : tnts.values()) {
            if (!t.isPaused() && t.getDetonateAt() != null) {
                int remaining = (int) Math.max(0, (t.getDetonateAt() - now) / 1000L);
                t.setPausedRemainingSeconds(remaining);
                t.setDetonateAt(null);
            }
            display.remove(t);
        }
        repo.flushAndShutdown(new ArrayList<>(tnts.values()));
    }

    public void spawnDisplaysInChunk(org.bukkit.Chunk chunk) {
        if (chunk == null) return;
        String ck = chunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        Set<UUID> ids = chunkIndex.get(ck);
        if (ids == null || ids.isEmpty()) return;
        for (UUID id : ids) {
            AssaultTnt t = tnts.get(id);
            if (t != null) display.spawn(t);
        }
    }

    public void removeAllByWar(long warId) {
        List<AssaultTnt> toRemove = new ArrayList<>();
        for (AssaultTnt t : tnts.values()) {
            if (t.getWarId() == warId) toRemove.add(t);
        }
        for (AssaultTnt t : toRemove) removeTnt(t, false);
    }

    private void cleanupOrphanDisplays() {
        var key = display.getDisplayKey();
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            if (!plugin.getTownyHook().api().isTownyWorld(w)) continue;
            for (org.bukkit.Chunk c : w.getLoadedChunks()) {
                for (org.bukkit.entity.Entity e : c.getEntities()) {
                    if (e instanceof TextDisplay td && td.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                        td.remove(); removed++;
                    }
                }
            }
        }
        if (removed > 0) plugin.getLogger().info("Убрано осиротевших TNT-дисплеев: " + removed);
    }

    private void loadFromDatabase() {
        long now = System.currentTimeMillis();
        boolean windowActive = windowManager.isCombatWindowActive();

        for (AssaultTnt t : repo.loadAll()) {
            War war = warManager.getWarById(t.getWarId());
            if (war == null || !war.isActive() || war.getPhase() != WarPhase.COMBAT) {
                plugin.getLogger().warning("TNT " + t.getTntId() + " ссылается на войну #"
                        + t.getWarId() + " не в COMBAT — удаляем.");
                repo.delete(t);
                continue;
            }
            String opponent = war.opposing(t.getOwnerTown());
            if (opponent == null || !t.getTargetTown().equalsIgnoreCase(opponent)) {
                repo.delete(t);
                continue;
            }

            World world = Bukkit.getWorld(t.getWorld());
            int cx = t.getX() >> 4, cz = t.getZ() >> 4;
            tnts.put(t.getTntId(), t);
            blockIndex.put(blockKey(t.getWorld(), t.getX(), t.getY(), t.getZ()), t.getTntId());
            chunkIndex.computeIfAbsent(chunkKey(t.getWorld(), cx, cz), k -> ConcurrentHashMap.newKeySet())
                    .add(t.getTntId());

            if (world != null) {
                Block block = world.getBlockAt(t.getX(), t.getY(), t.getZ());
                if (block.getType() != Material.TNT) block.setType(Material.TNT, false);
            }

            boolean truceActive = plugin.getWarshopManager() != null
                    && plugin.getWarshopManager().isTruceActive(t.getWarId());
            boolean canRun = windowActive && !truceActive;

            if (t.isPaused()) {
                if (canRun) {
                    Integer remaining = t.getPausedRemainingSeconds();
                    if (remaining == null) remaining = 0;
                    t.setDetonateAt(now + remaining * 1000L);
                    t.setPausedRemainingSeconds(null);
                    repo.update(t);
                }
            } else if (t.getDetonateAt() != null) {
                if (t.getDetonateAt() <= now) {
                    if (canRun) {
                        explode(t);
                        removeTnt(t, false);
                        continue;
                    } else {
                        t.setPausedRemainingSeconds(0);
                        t.setDetonateAt(null);
                        repo.update(t);
                    }
                } else if (!canRun) {
                    int remaining = (int) Math.max(0, (t.getDetonateAt() - now) / 1000L);
                    t.setPausedRemainingSeconds(remaining);
                    t.setDetonateAt(null);
                    repo.update(t);
                }
            }

            display.spawn(t);
        }
        plugin.getLogger().info("Загружено Штурмовых ТНТ из БД: " + tnts.size());
    }

    @Override public void onWindowOpen() {
        long now = System.currentTimeMillis();
        for (AssaultTnt t : tnts.values()) {
            if (!t.isPaused()) continue;
            War w = warManager.getWarById(t.getWarId());
            if (w != null && plugin.getWarshopManager() != null
                    && plugin.getWarshopManager().isTruceActive(w.getId())) continue;
            Integer remaining = t.getPausedRemainingSeconds();
            if (remaining == null) remaining = 0;
            t.setDetonateAt(now + remaining * 1000L);
            t.setPausedRemainingSeconds(null);
            repo.update(t); display.update(t);
        }
    }

    @Override public void onWindowClose() {
        long now = System.currentTimeMillis();
        for (AssaultTnt t : tnts.values()) {
            if (t.isPaused()) continue;
            Long finish = t.getDetonateAt();
            int remaining = finish == null ? 0 : (int) Math.max(0, (finish - now) / 1000L);
            t.setPausedRemainingSeconds(remaining);
            t.setDetonateAt(null);
            repo.update(t); display.update(t);
        }
    }

    public boolean place(Player player, Block block) {
        if (!windowManager.isCombatWindowActive()) {
            player.sendActionBar(MM.deserialize("<red>Штурмовой ТНТ можно ставить только во время боевого окна.</red>"));
            return false;
        }
        if (!block.getType().isAir() && block.getType() != Material.SHORT_GRASS
                && block.getType() != Material.TALL_GRASS) {
            player.sendActionBar(MM.deserialize("<red>Здесь нельзя поставить ТНТ — блок занят.</red>"));
            return false;
        }

        Location loc = block.getLocation();
        World world = loc.getWorld();
        if (world == null) return false;

        String ck = chunkKey(world.getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        Set<UUID> inChunk = chunkIndex.get(ck);
        int max = config.getAssaultTntMaxPerChunk();
        if (inChunk != null && inChunk.size() >= max) {
            player.sendActionBar(MM.deserialize("<red>В этом чанке уже " + max + " активных ТНТ.</red>"));
            return false;
        }

        if (blockIndex.containsKey(blockKey(world.getName(), block.getX(), block.getY(), block.getZ()))) {
            player.sendActionBar(MM.deserialize("<red>Здесь уже стоит ТНТ.</red>"));
            return false;
        }

        Town ownTown = plugin.getTownyHook().getTownOf(player);
        if (ownTown == null) return false;

        War war = warManager.getWar(ownTown.getName());
        if (war == null || war.getPhase() != WarPhase.COMBAT) {
            player.sendActionBar(MM.deserialize("<red>Ваш город не в фазе боя.</red>"));
            return false;
        }

        if (plugin.getWarshopManager() != null
                && plugin.getWarshopManager().isTruceActive(war.getId())) {
            player.sendActionBar(MM.deserialize("<red>Во время перемирия ставить ТНТ нельзя.</red>"));
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
            player.sendActionBar(MM.deserialize("<red>Нельзя ставить ТНТ в своём городе.</red>"));
            return false;
        }
        String opponentName = war.opposing(ownTown.getName());
        if (opponentName == null || !targetTown.getName().equalsIgnoreCase(opponentName)) {
            player.sendActionBar(MM.deserialize(
                    "<red>ТНТ можно ставить только в чанках города-противника по вашей войне.</red>"));
            return false;
        }

        int durability = config.getAssaultTntDurability();
        int fuseSec = config.getAssaultTntFuseSeconds();
        long now = System.currentTimeMillis();
        UUID tntId = UUID.randomUUID();

        block.setType(Material.TNT, false);

        AssaultTnt t = new AssaultTnt(tntId, war.getId(), ownTown.getName(), targetTown.getName(),
                world.getName(), block.getX(), block.getY(), block.getZ(),
                block.getX() >> 4, block.getZ() >> 4,
                durability, durability, now, now + fuseSec * 1000L, null);

        tnts.put(tntId, t);
        blockIndex.put(blockKey(world.getName(), block.getX(), block.getY(), block.getZ()), tntId);
        chunkIndex.computeIfAbsent(ck, k -> ConcurrentHashMap.newKeySet()).add(tntId);
        repo.insert(t);

        display.spawn(t);

        plugin.getWarLogRepository().log(war.getId(), player.getName(), "ASSAULT_TNT_PLACED",
                "x=" + block.getX() + ";y=" + block.getY() + ";z=" + block.getZ()
                        + ";target=" + targetTown.getName());
        return true;
    }

    public boolean canPlayerBreakTnt(Player player, AssaultTnt t) {
        if (player == null || t == null) return false;
        if (!windowManager.isCombatWindowActive()) return false;
        War war = warManager.getWarById(t.getWarId());
        if (war == null || !war.isActive() || war.getPhase() != WarPhase.COMBAT) return false;
        if (plugin.getWarshopManager() != null
                && plugin.getWarshopManager().isTruceActive(war.getId())) return false;
        Town playerTown = plugin.getTownyHook().getTownOf(player);
        if (playerTown == null) return false;
        return war.involves(playerTown.getName());
    }

    public boolean handleBreak(Player player, Block block) {
        String key = blockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        UUID id = blockIndex.get(key);
        if (id == null) return false;
        AssaultTnt t = tnts.get(id);
        if (t == null) return false;

        if (!canPlayerBreakTnt(player, t)) return false;

        int newDur = t.getDurability() - 1;
        if (newDur <= 0) {
            notifyBroken(t);
            removeTnt(t, true);
            player.sendActionBar(MM.deserialize("<red>Штурмовой ТНТ уничтожен!</red>"));
            plugin.getWarLogRepository().log(t.getWarId(), player.getName(), "ASSAULT_TNT_BROKEN",
                    "x=" + t.getX() + ";y=" + t.getY() + ";z=" + t.getZ());
            return true;
        }
        t.setDurability(newDur);
        repo.update(t); display.update(t);
        player.sendActionBar(MM.deserialize(
                "<yellow>Прочность ТНТ: <white>" + newDur + "/" + t.getMaxDurability() + "</white></yellow>"));
        return false;
    }

    private void notifyBroken(AssaultTnt t) {
        Town ownerTown = plugin.getTownyHook().getTown(t.getOwnerTown());
        Town targetTown = plugin.getTownyHook().getTown(t.getTargetTown());
        if (ownerTown != null) {
            var msg = MM.deserialize("<red>Ваш Штурмовой ТНТ обезврежен!</red>");
            for (var r : ownerTown.getResidents()) {
                Player p = r.getPlayer(); if (p != null) p.sendMessage(msg);
            }
        }
        if (targetTown != null) {
            var msg = MM.deserialize("<green>Вы обезвредили вражеский ТНТ!</green>");
            for (var r : targetTown.getResidents()) {
                Player p = r.getPlayer(); if (p != null) p.sendMessage(msg);
            }
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean windowActive = windowManager.isCombatWindowActive();

        for (AssaultTnt t : new ArrayList<>(tnts.values())) {
            War war = warManager.getWarById(t.getWarId());
            if (war == null || !war.isActive() || war.getPhase() != WarPhase.COMBAT) {
                removeTnt(t, false);
                continue;
            }
            boolean truceActive = plugin.getWarshopManager() != null
                    && plugin.getWarshopManager().isTruceActive(war.getId());
            boolean shouldRun = windowActive && !truceActive;

            if (t.isPaused()) {
                if (!shouldRun) continue;
                Integer saved = t.getPausedRemainingSeconds();
                if (saved == null) saved = 0;
                t.setDetonateAt(now + saved * 1000L);
                t.setPausedRemainingSeconds(null);
                repo.update(t); display.update(t);
                continue;
            }

            if (!shouldRun) {
                int remaining = (int) Math.max(0, (t.getDetonateAt() - now) / 1000L);
                t.setPausedRemainingSeconds(remaining);
                t.setDetonateAt(null);
                repo.update(t); display.update(t);
                continue;
            }

            if (t.getDetonateAt() != null && now >= t.getDetonateAt()) {
                explode(t);
                removeTnt(t, false);
                continue;
            }
            display.update(t);
        }
    }

    private void explode(AssaultTnt t) {
        World world = Bukkit.getWorld(t.getWorld());
        if (world == null) return;
        Location center = new Location(world, t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5);

        float power = config.getAssaultTntPower();

        // 1. Визуал + звук. НЕ используем world.createExplosion: даже с
        //    breakBlocks=false клиент локально прогнозирует разрушение
        //    блоков (в т.ч. наших баннеров) — а это даёт ghost-блоки и
        //    "POI data mismatch" на сервере.
        world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, center, 1);
        world.spawnParticle(org.bukkit.Particle.LARGE_SMOKE, center, 40, 1.5, 1.0, 1.5, 0.05);
        world.spawnParticle(org.bukkit.Particle.CLOUD, center, 25, 2.0, 1.5, 2.0, 0.05);
        world.playSound(center, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 1.0f);

        // 2. Урон сущностям в радиусе power * 2 (примерная визуальная дистанция).
        double damageRadius = power * 2.0;
        double damageRadiusSq = damageRadius * damageRadius;
        float damageAmount = power * 6f;
        for (org.bukkit.entity.Entity e : world.getNearbyEntities(center, damageRadius, damageRadius, damageRadius)) {
            if (e.isDead() || !e.isValid()) continue;
            if (e instanceof Player p && p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (e instanceof org.bukkit.entity.ItemDisplay || e instanceof org.bukkit.entity.TextDisplay
                    || e instanceof org.bukkit.entity.ItemFrame || e instanceof org.bukkit.entity.Painting) continue;

            // damage() объявлен в Damageable, а не в Entity — без сужения
            // компилятор не видит метод.
            if (!(e instanceof org.bukkit.entity.Damageable dmg)) continue;

            double distSq = e.getLocation().distanceSquared(center);
            if (distSq > damageRadiusSq) continue;
            double falloff = 1.0 - Math.sqrt(distSq) / damageRadius;
            dmg.damage(damageAmount * Math.max(0.1, falloff));
        }

        // 3. Ручное разрушение блоков по сфере.
        int r = (int) Math.ceil(power);
        int cx = t.getX(), cy = t.getY(), cz = t.getZ();
        FlagManager fm = plugin.getFlagManager();
        long now = System.currentTimeMillis();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > power * power) continue;
                    Block b = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (b.getType().isAir()) continue;

                    // Сам себя не бьём.
                    if (b.getX() == cx && b.getY() == cy && b.getZ() == cz) continue;

                    // Флаги — не трогаем: ни сам баннер, ни соседние блоки (опоры).
                    // Иначе клиент ванильно удаляет баннер без опоры, а на сервере
                    // флаг остаётся живым — «висит в воздухе».
                    if (fm != null) {
                        if (fm.isFlagBlock(b)) continue;
                        if (fm.isFlagBlock(b.getRelative(BlockFace.UP))
                                || fm.isFlagBlock(b.getRelative(BlockFace.DOWN))
                                || fm.isFlagBlock(b.getRelative(BlockFace.NORTH))
                                || fm.isFlagBlock(b.getRelative(BlockFace.SOUTH))
                                || fm.isFlagBlock(b.getRelative(BlockFace.EAST))
                                || fm.isFlagBlock(b.getRelative(BlockFace.WEST))) {
                            continue;
                        }
                    }

                    // Дружественный TNT — chain-trigger.
                    String bk = blockKey(world.getName(), b.getX(), b.getY(), b.getZ());
                    UUID otherId = blockIndex.get(bk);
                    if (otherId != null && !otherId.equals(t.getTntId())) {
                        AssaultTnt other = tnts.get(otherId);
                        if (other != null) {
                            other.setDetonateAt(now);
                            other.setPausedRemainingSeconds(null);
                        }
                        continue;
                    }

                    if (b.getType().getBlastResistance() > BLAST_RESISTANCE_THRESHOLD) continue;

                    // setType(AIR, false) — без физики, без BlockBreakEvent,
                    // без каскадных обновлений. Иначе — POI mismatch в
                    // консоли и ghost-блоки у клиентов.
                    b.setType(Material.AIR, false);
                }
            }
        }

        plugin.getWarLogRepository().log(t.getWarId(), null, "ASSAULT_TNT_EXPLODED",
                "x=" + cx + ";y=" + cy + ";z=" + cz);
    }

    private void removeTnt(AssaultTnt t, boolean dropItem) {
        tnts.remove(t.getTntId());
        String bk = blockKey(t.getWorld(), t.getX(), t.getY(), t.getZ());
        blockIndex.remove(bk);
        String ck = chunkKey(t.getWorld(), t.getX() >> 4, t.getZ() >> 4);
        Set<UUID> set = chunkIndex.get(ck);
        if (set != null) {
            set.remove(t.getTntId());
            if (set.isEmpty()) chunkIndex.remove(ck);
        }
        display.remove(t);
        repo.delete(t);

        World world = Bukkit.getWorld(t.getWorld());
        if (world == null) return;
        Block block = world.getBlockAt(t.getX(), t.getY(), t.getZ());
        if (block.getType() == Material.TNT) block.setType(Material.AIR, false);

        if (dropItem) {
            Location loc = new Location(world, t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5);
            world.dropItemNaturally(loc, AssaultTntItemFactory.create(plugin));
        }
    }

    public AssaultTnt getTntAt(Block block) {
        String key = blockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        UUID id = blockIndex.get(key);
        return id == null ? null : tnts.get(id);
    }

    public boolean isTntBlock(Block block) {
        return blockIndex.containsKey(blockKey(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ()));
    }

    public Collection<AssaultTnt> allTnts() {
        return Collections.unmodifiableCollection(tnts.values());
    }

    private static String blockKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
    private static String chunkKey(String world, int cx, int cz) {
        return world + ":" + cx + ":" + cz;
    }
}