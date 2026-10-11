package ru.example.townywar.assault;

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

public final class AssaultTntRepository {

    private static final Logger LOG = Logger.getLogger("TownyWar");
    private static final long FLUSH_TIMEOUT_SECONDS = 5L;

    private final Database db;
    private final JavaPlugin plugin;
    private final ExecutorService executor;

    public AssaultTntRepository(Database db, JavaPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread th = new Thread(r, "TownyWar-DB-AssaultTnt");
            th.setDaemon(true);
            return th;
        });
    }

    public void insert(AssaultTnt t) {
        String sql = "INSERT INTO assault_tnt (war_id, owner_town, target_town, world, x, y, z, chunk_x, chunk_z, durability, max_durability, placed_at, detonate_at, paused_remaining_seconds) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, t.getWarId());
            ps.setString(2, t.getOwnerTown());
            ps.setString(3, t.getTargetTown());
            ps.setString(4, t.getWorld());
            ps.setInt(5, t.getX());
            ps.setInt(6, t.getY());
            ps.setInt(7, t.getZ());
            ps.setInt(8, t.getChunkX());
            ps.setInt(9, t.getChunkZ());
            ps.setInt(10, t.getDurability());
            ps.setInt(11, t.getMaxDurability());
            ps.setLong(12, t.getPlacedAt());
            setNullableLong(ps, 13, t.getDetonateAt());
            setNullableInt(ps, 14, t.getPausedRemainingSeconds());
            ps.executeUpdate();
        } catch (SQLException e) { LOG.log(Level.WARNING, "assault_tnt insert failed", e); }
    }

    public void update(AssaultTnt t) {
        if (!plugin.isEnabled()) return;
        final int durability = t.getDurability();
        final Long detonateAt = t.getDetonateAt();
        final Integer paused = t.getPausedRemainingSeconds();
        final long warId = t.getWarId();
        final String ownerTown = t.getOwnerTown();
        final String world = t.getWorld();
        final int x = t.getX(), y = t.getY(), z = t.getZ();

        submit(() -> {
            String sql = "UPDATE assault_tnt SET durability=?, detonate_at=?, paused_remaining_seconds=? WHERE war_id=? AND owner_town=? AND world=? AND x=? AND y=? AND z=?";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, durability);
                setNullableLong(ps, 2, detonateAt);
                setNullableInt(ps, 3, paused);
                ps.setLong(4, warId);
                ps.setString(5, ownerTown);
                ps.setString(6, world);
                ps.setInt(7, x);
                ps.setInt(8, y);
                ps.setInt(9, z);
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "assault_tnt async update failed", e);
            }
        });
    }

    public void delete(AssaultTnt t) {
        if (!plugin.isEnabled()) return;
        final long warId = t.getWarId();
        final String ownerTown = t.getOwnerTown();
        final String world = t.getWorld();
        final int x = t.getX(), y = t.getY(), z = t.getZ();

        submit(() -> {
            String sql = "DELETE FROM assault_tnt WHERE war_id=? AND owner_town=? AND world=? AND x=? AND y=? AND z=?";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, warId);
                ps.setString(2, ownerTown);
                ps.setString(3, world);
                ps.setInt(4, x);
                ps.setInt(5, y);
                ps.setInt(6, z);
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "assault_tnt async delete failed", e);
            }
        });
    }

    private void submit(Runnable r) {
        try { executor.execute(r); }
        catch (RejectedExecutionException e) { LOG.log(Level.FINE, "assault_tnt task rejected"); }
    }

    public void updateSync(AssaultTnt t) {
        String sql = "UPDATE assault_tnt SET durability=?, detonate_at=?, paused_remaining_seconds=? WHERE war_id=? AND owner_town=? AND world=? AND x=? AND y=? AND z=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, t.getDurability());
            setNullableLong(ps, 2, t.getDetonateAt());
            setNullableInt(ps, 3, t.getPausedRemainingSeconds());
            ps.setLong(4, t.getWarId());
            ps.setString(5, t.getOwnerTown());
            ps.setString(6, t.getWorld());
            ps.setInt(7, t.getX());
            ps.setInt(8, t.getY());
            ps.setInt(9, t.getZ());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "assault_tnt sync update failed", e);
        }
    }

    public void flushAndShutdown(Collection<AssaultTnt> active) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        for (AssaultTnt t : active) updateSync(t);
    }

    public List<AssaultTnt> loadAll() {
        List<AssaultTnt> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM assault_tnt");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { LOG.log(Level.WARNING, "assault_tnt load failed", e); }
        return list;
    }

    private static AssaultTnt map(ResultSet rs) throws SQLException {
        long warId = rs.getLong("war_id");
        String ownerTown = rs.getString("owner_town");
        String targetTown = rs.getString("target_town");
        String world = rs.getString("world");
        int x = rs.getInt("x"), y = rs.getInt("y"), z = rs.getInt("z");
        UUID tntId = stableId(warId, ownerTown, world, x, y, z);
        return new AssaultTnt(tntId, warId, ownerTown, targetTown,
                world, x, y, z,
                rs.getInt("chunk_x"), rs.getInt("chunk_z"),
                rs.getInt("durability"), rs.getInt("max_durability"),
                rs.getLong("placed_at"),
                nullableLong(rs, "detonate_at"),
                nullableInt(rs, "paused_remaining_seconds"));
    }

    private static UUID stableId(long warId, String ownerTown, String world, int x, int y, int z) {
        String key = "tnt:" + warId + ":" + ownerTown.toLowerCase() + ":" + world + ":" + x + ":" + y + ":" + z;
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