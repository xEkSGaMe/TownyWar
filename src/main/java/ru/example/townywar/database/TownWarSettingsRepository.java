package ru.example.townywar.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TownWarSettingsRepository {

    private static final Logger LOG = Logger.getLogger("TownyWar");
    private final Database db;

    public TownWarSettingsRepository(Database db) { this.db = db; }

    public record TownSettings(int maxFlags, int startingBonus) { }

    public Map<String, TownSettings> loadAll() {
        Map<String, TownSettings> map = new HashMap<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT town, max_concurrent_capture_flags, starting_points_bonus FROM town_war_settings");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String town = rs.getString("town");
                if (town == null) continue;
                map.put(town.toLowerCase(), new TownSettings(
                        rs.getInt("max_concurrent_capture_flags"),
                        rs.getInt("starting_points_bonus")));
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "town_war_settings load failed", e);
        }
        return map;
    }

    /**
     * Единый upsert обоих полей. Частичный upsert невозможен без знания
     * второго значения, поэтому callers передают актуальные числа из
     * кэша WarManager.
     */
    public void upsert(String town, int maxFlags, int startingBonus) {
        if (town == null) return;
        String sql = "INSERT INTO town_war_settings (town, max_concurrent_capture_flags, starting_points_bonus) "
                + "VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "  max_concurrent_capture_flags = VALUES(max_concurrent_capture_flags), "
                + "  starting_points_bonus = VALUES(starting_points_bonus)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, town);
            ps.setInt(2, maxFlags);
            ps.setInt(3, startingBonus);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "town_war_settings upsert failed", e);
        }
    }

    public void delete(String town) {
        if (town == null) return;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM town_war_settings WHERE town = ?")) {
            ps.setString(1, town);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "town_war_settings delete failed", e);
        }
    }
}