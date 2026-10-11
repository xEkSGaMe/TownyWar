package ru.example.townywar.warshop;

import org.bukkit.plugin.java.JavaPlugin;
import ru.example.townywar.database.Database;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WarshopRepository {

    private static final Logger LOG = Logger.getLogger("TownyWar");
    private static final long FLUSH_TIMEOUT_SECONDS = 5L;

    public record TownState(long inspirationReadyAt, long truceReadyAt,
                            long mercenariesReadyAt, long inspirationUntil) { }

    private final Database db;
    private final JavaPlugin plugin;
    private final ExecutorService executor;

    public WarshopRepository(Database db, JavaPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread th = new Thread(r, "TownyWar-DB-Warshop");
            th.setDaemon(true);
            return th;
        });
    }

    public Map<String, TownState> loadAll() {
        Map<String, TownState> map = new HashMap<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT town, inspiration_ready_at, truce_ready_at, mercenaries_ready_at, inspiration_until FROM town_warshop_state");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String town = rs.getString("town");
                if (town == null) continue;
                map.put(town.toLowerCase(), new TownState(
                        rs.getLong("inspiration_ready_at"),
                        rs.getLong("truce_ready_at"),
                        rs.getLong("mercenaries_ready_at"),
                        rs.getLong("inspiration_until")));
            }
        } catch (SQLException e) { LOG.log(Level.WARNING, "warshop state load failed", e); }
        return map;
    }

    public void upsertTownState(String town, TownState state) {
        if (!plugin.isEnabled()) return;
        final String key = town.toLowerCase();
        submit(() -> {
            String sql = "INSERT INTO town_warshop_state (town, inspiration_ready_at, truce_ready_at, mercenaries_ready_at, inspiration_until) "
                    + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                    + "inspiration_ready_at = VALUES(inspiration_ready_at), "
                    + "truce_ready_at = VALUES(truce_ready_at), "
                    + "mercenaries_ready_at = VALUES(mercenaries_ready_at), "
                    + "inspiration_until = VALUES(inspiration_until)";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, key);
                ps.setLong(2, state.inspirationReadyAt());
                ps.setLong(3, state.truceReadyAt());
                ps.setLong(4, state.mercenariesReadyAt());
                ps.setLong(5, state.inspirationUntil());
                ps.executeUpdate();
            } catch (SQLException e) { LOG.log(Level.WARNING, "warshop state upsert failed", e); }
        });
    }

    public Map<Long, Long> loadTruces() {
        Map<Long, Long> map = new HashMap<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT war_id, until_at FROM warshop_truce");
             ResultSet rs = ps.executeQuery()) {
            long now = System.currentTimeMillis();
            while (rs.next()) {
                long until = rs.getLong("until_at");
                if (until > now) map.put(rs.getLong("war_id"), until);
            }
        } catch (SQLException e) { LOG.log(Level.WARNING, "warshop truce load failed", e); }
        return map;
    }

    public void upsertTruce(long warId, long untilAt) {
        if (!plugin.isEnabled()) return;
        submit(() -> {
            String sql = "INSERT INTO warshop_truce (war_id, until_at) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE until_at = VALUES(until_at)";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, warId);
                ps.setLong(2, untilAt);
                ps.executeUpdate();
            } catch (SQLException e) { LOG.log(Level.WARNING, "warshop truce upsert failed", e); }
        });
    }

    public void deleteTruce(long warId) {
        if (!plugin.isEnabled()) return;
        submit(() -> {
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM warshop_truce WHERE war_id=?")) {
                ps.setLong(1, warId);
                ps.executeUpdate();
            } catch (SQLException e) { LOG.log(Level.WARNING, "warshop truce delete failed", e); }
        });
    }

    private void submit(Runnable r) {
        try { executor.execute(r); }
        catch (RejectedExecutionException e) { LOG.log(Level.FINE, "warshop task rejected"); }
    }

    public void flushAndShutdown(Runnable onFlush) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); executor.shutdownNow(); }
        try { onFlush.run(); } catch (Throwable ignored) { }
    }
}