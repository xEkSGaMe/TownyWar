package ru.example.townywar.negotiation;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.townywar.database.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TributeRepository {

    private static final Logger LOG = Logger.getLogger("TownyWar");

    public record TributeEntry(long id, long warId, String payerTown, String receiverTown,
                               int percent, long nextAt, boolean active) { }

    private final Database db;
    private final JavaPlugin plugin;

    public TributeRepository(Database db, JavaPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
    }

    /** Асинхронный insert — используется NegotiationManager при applyOutcome(TRIBUTE). */
    public void insert(long warId, String payer, String receiver, int percent, long nextAt) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO tributes (war_id, payer_town, receiver_town, percent, next_at, active) VALUES (?, ?, ?, ?, ?, 1)";
            try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, warId);
                ps.setString(2, payer);
                ps.setString(3, receiver);
                ps.setInt(4, percent);
                ps.setLong(5, nextAt);
                ps.executeUpdate();
            } catch (SQLException e) { LOG.log(Level.WARNING, "tribute insert failed", e); }
        });
    }

    /**
     * C11: синхронный insert. Используется из applyTribute() — трибут
     * должен быть записан ДО endWar, иначе при внезапном выключении сервера
     * между insert (async) и endWar трибут потеряется, а война в БД будет
     * ENDED. Запись редкая — блокировка main-thread пренебрежима.
     */
    public void insertSync(long warId, String payer, String receiver, int percent, long nextAt) {
        String sql = "INSERT INTO tributes (war_id, payer_town, receiver_town, percent, next_at, active) VALUES (?, ?, ?, ?, ?, 1)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, warId);
            ps.setString(2, payer);
            ps.setString(3, receiver);
            ps.setInt(4, percent);
            ps.setLong(5, nextAt);
            ps.executeUpdate();
        } catch (SQLException e) { LOG.log(Level.WARNING, "tribute sync insert failed", e); }
    }

    public List<TributeEntry> loadAllActive() {
        List<TributeEntry> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM tributes WHERE active = 1");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new TributeEntry(rs.getLong("id"), rs.getLong("war_id"),
                        rs.getString("payer_town"), rs.getString("receiver_town"),
                        rs.getInt("percent"), rs.getLong("next_at"), rs.getBoolean("active")));
            }
        } catch (SQLException e) { LOG.log(Level.WARNING, "tribute load failed", e); }
        return list;
    }

    /** Async-версия. Оставлена на случай, если понадобится вне Scheduler. */
    public void updateNextAt(long id, long nextAt) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement("UPDATE tributes SET next_at=? WHERE id=?")) {
                ps.setLong(1, nextAt);
                ps.setLong(2, id);
                ps.executeUpdate();
            } catch (SQLException e) { LOG.log(Level.WARNING, "tribute update failed", e); }
        });
    }

    /**
     * Синхронная версия — основная для TributeScheduler.
     * Запись редкая (раз в сутки на трибут), main-thread-блокировка пренебрежима,
     * зато исключён риск, что async не успел лечь и трибут сработает повторно после рестарта.
     */
    public void updateNextAtSync(long id, long nextAt) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE tributes SET next_at=? WHERE id=?")) {
            ps.setLong(1, nextAt);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) { LOG.log(Level.WARNING, "tribute sync update failed", e); }
    }

    /** Async-версия deactivate. */
    public void deactivate(long id) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement("UPDATE tributes SET active=0 WHERE id=?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            } catch (SQLException e) { LOG.log(Level.WARNING, "tribute deactivate failed", e); }
        });
    }

    /** Синхронная версия deactivate — для TributeScheduler. */
    public void deactivateSync(long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE tributes SET active=0 WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { LOG.log(Level.WARNING, "tribute sync deactivate failed", e); }
    }
}