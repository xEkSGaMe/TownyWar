package ru.example.townywar.database;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WarLogRepository {

    private final Database db;
    private final JavaPlugin plugin;
    private final Logger logger;

    public WarLogRepository(Database db, JavaPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * B1: async-лог для горячих путей (слом/постановка блока, убийство,
     * удар по флагу, заблокированная команда, override). Не блокирует
     * main-thread. При выключенном плагине — no-op (БД уже может быть
     * закрыта).
     * <p>
     * Потеря логов при жёстком shutdown приемлема: все критичные события
     * (insert войны, insert флага, applyOutcome, endWar) имеют синхронные
     * DB-операции и не зависят от этой записи.
     */
    public void log(Long warId, String actor, String event, String payload) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> logSync(warId, actor, event, payload));
    }

    /**
     * B1: синхронная версия — только для shutdown-путей и случаев, когда
     * потеря записи критична. В горячих путях не использовать.
     */
    public void logSync(Long warId, String actor, String event, String payload) {
        String sql = "INSERT INTO war_log (war_id, ts, actor, event, payload) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (warId == null) ps.setNull(1, Types.BIGINT); else ps.setLong(1, warId);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, actor);
            ps.setString(4, event);
            ps.setString(5, payload);
            ps.executeUpdate();
        } catch (SQLException e) { logger.log(Level.WARNING, "war_log insert failed", e); }
    }
}