package ru.example.townywar.flag;

import org.bukkit.plugin.java.JavaPlugin;
import ru.example.townywar.database.Database;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FlagRepository {

    private static final Logger LOG = Logger.getLogger("TownyWar");
    private static final long FLUSH_TIMEOUT_SECONDS = 5L;

    private final Database db;
    private final JavaPlugin plugin;

    /**
     * C2: сериализация всех async-апдейтов/делитов флагов через один
     * single-thread executor. FIFO-порядок исключает гонку при параллельных
     * срабатываниях tick/window-hooks/handleBreak и при shutdown.
     */
    private final ExecutorService executor;

    public FlagRepository(Database db, JavaPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread th = new Thread(r, "TownyWar-DB-Flags");
            th.setDaemon(true);
            return th;
        });
    }

    public void insert(PlacedFlag f) {
        String sql = "INSERT INTO placed_flags (war_id, type, owner_town, placed_by, target_town, world, x, y, z, chunk_x, chunk_z, durability, max_durability, placed_at, capture_finish_at, paused_remaining_seconds) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, f.getWarId());
            ps.setString(2, f.getType().name());
            ps.setString(3, f.getOwnerTown());
            ps.setString(4, f.getPlacedBy());   // может быть null — это ок
            ps.setString(5, f.getTargetTown());
            ps.setString(6, f.getWorld());
            ps.setInt(7, f.getX());
            ps.setInt(8, f.getY());
            ps.setInt(9, f.getZ());
            ps.setInt(10, f.getChunkX());
            ps.setInt(11, f.getChunkZ());
            ps.setInt(12, f.getDurability());
            ps.setInt(13, f.getMaxDurability());
            ps.setLong(14, f.getPlacedAt());
            setNullableLong(ps, 15, f.getCaptureFinishAt());
            setNullableInt(ps, 16, f.getPausedRemainingSeconds());
            ps.executeUpdate();
        } catch (SQLException e) { LOG.log(Level.WARNING, "flag insert failed", e); }
    }

    public void update(PlacedFlag f) {
        if (!plugin.isEnabled()) return;
        final int durability = f.getDurability();
        final Long captureFinish = f.getCaptureFinishAt();
        final Integer pausedRemaining = f.getPausedRemainingSeconds();
        final long warId = f.getWarId();
        final String ownerTown = f.getOwnerTown();
        final String type = f.getType().name();
        final String world = f.getWorld();
        final int x = f.getX(), y = f.getY(), z = f.getZ();

        submit(() -> {
            String sql = "UPDATE placed_flags SET durability=?, capture_finish_at=?, paused_remaining_seconds=? WHERE war_id=? AND owner_town=? AND type=? AND world=? AND x=? AND y=? AND z=?";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, durability);
                setNullableLong(ps, 2, captureFinish);
                setNullableInt(ps, 3, pausedRemaining);
                ps.setLong(4, warId);
                ps.setString(5, ownerTown);
                ps.setString(6, type);
                ps.setString(7, world);
                ps.setInt(8, x);
                ps.setInt(9, y);
                ps.setInt(10, z);
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "flag async update failed", e);
            }
        });
    }

    public void delete(PlacedFlag f) {
        if (!plugin.isEnabled()) return;
        final long warId = f.getWarId();
        final String ownerTown = f.getOwnerTown();
        final String type = f.getType().name();
        final String world = f.getWorld();
        final int x = f.getX(), y = f.getY(), z = f.getZ();

        submit(() -> {
            String sql = "DELETE FROM placed_flags WHERE war_id=? AND owner_town=? AND type=? AND world=? AND x=? AND y=? AND z=?";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, warId);
                ps.setString(2, ownerTown);
                ps.setString(3, type);
                ps.setString(4, world);
                ps.setInt(5, x);
                ps.setInt(6, y);
                ps.setInt(7, z);
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "flag async delete failed", e);
            }
        });
    }

    private void submit(Runnable r) {
        try { executor.execute(r); }
        catch (RejectedExecutionException e) {
            LOG.log(Level.FINE, "flag task rejected for closed executor");
        }
    }

    public void updateSync(PlacedFlag f) {
        String sql = "UPDATE placed_flags SET durability=?, capture_finish_at=?, paused_remaining_seconds=? WHERE war_id=? AND owner_town=? AND type=? AND world=? AND x=? AND y=? AND z=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, f.getDurability());
            setNullableLong(ps, 2, f.getCaptureFinishAt());
            setNullableInt(ps, 3, f.getPausedRemainingSeconds());
            ps.setLong(4, f.getWarId());
            ps.setString(5, f.getOwnerTown());
            ps.setString(6, f.getType().name());
            ps.setString(7, f.getWorld());
            ps.setInt(8, f.getX());
            ps.setInt(9, f.getY());
            ps.setInt(10, f.getZ());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "flag sync update failed", e);
        }
    }

    public void deleteSync(PlacedFlag f) {
        String sql = "DELETE FROM placed_flags WHERE war_id=? AND owner_town=? AND type=? AND world=? AND x=? AND y=? AND z=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, f.getWarId());
            ps.setString(2, f.getOwnerTown());
            ps.setString(3, f.getType().name());
            ps.setString(4, f.getWorld());
            ps.setInt(5, f.getX());
            ps.setInt(6, f.getY());
            ps.setInt(7, f.getZ());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "flag sync delete failed", e);
        }
    }

    /**
     * C2: shutdown-путь. Дожидаемся pending async-тасков, чтобы sync-запись
     * не перезатиралась поздним async-апдейтом, потом sync-фиксируем
     * актуальное состояние, потом закрываем executor.
     */
    public void flushAndShutdown(Collection<PlacedFlag> active) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        for (PlacedFlag f : active) updateSync(f);
    }

    public List<PlacedFlag> loadAll() {
        List<PlacedFlag> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM placed_flags");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { LOG.log(Level.WARNING, "flag load failed", e); }
        return list;
    }

    /**
     * D1: flagId теперь детерминирован — вычисляется из составного ключа
     * записи. Между рестартами он не меняется, поэтому внешние интеграции
     * (карты, статистика, кэши) могут на него полагаться.
     */
    private static PlacedFlag map(ResultSet rs) throws SQLException {
        long warId = rs.getLong("war_id");
        FlagType type = FlagType.valueOf(rs.getString("type"));
        String ownerTown = rs.getString("owner_town");
        String placedBy = rs.getString("placed_by");
        String targetTown = rs.getString("target_town");
        String world = rs.getString("world");
        int x = rs.getInt("x"), y = rs.getInt("y"), z = rs.getInt("z");

        UUID flagId = stableId(warId, ownerTown, type, world, x, y, z);

        return new PlacedFlag(flagId, warId, type,
                ownerTown, placedBy, targetTown,
                world, x, y, z,
                rs.getInt("chunk_x"), rs.getInt("chunk_z"),
                rs.getInt("durability"), rs.getInt("max_durability"),
                rs.getLong("placed_at"),
                nullableLong(rs, "capture_finish_at"),
                nullableInt(rs, "paused_remaining_seconds"));
    }

    private static UUID stableId(long warId, String ownerTown, FlagType type,
                                 String world, int x, int y, int z) {
        String key = warId + ":" + ownerTown.toLowerCase() + ":" + type.name()
                + ":" + world + ":" + x + ":" + y + ":" + z;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static Long nullableLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col); return rs.wasNull() ? null : v;
    }
    private static Integer nullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col); return rs.wasNull() ? null : v;
    }
    private static void setNullableLong(PreparedStatement ps, int i, Long v) throws SQLException {
        if (v == null) ps.setNull(i, Types.BIGINT); else ps.setLong(i, v);
    }
    private static void setNullableInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, v);
    }
}