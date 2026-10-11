package ru.example.townywar.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.townywar.config.ConfigManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public final class Database {

    private final JavaPlugin plugin;
    private final ConfigManager.DatabaseConfig cfg;
    private HikariDataSource ds;

    public Database(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.cfg = configManager.getDatabaseConfig();
    }

    public boolean connect() {
        try {
            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:mysql://" + cfg.host() + ":" + cfg.port() + "/" + cfg.name()
                    + "?useSSL=" + cfg.useSsl()
                    + "&characterEncoding=utf8&useUnicode=true"
                    + "&serverTimezone=Europe/Moscow"
                    + "&allowPublicKeyRetrieval=true"
                    + "&cachePrepStmts=true&useServerPrepStmts=true"
                    + "&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048");
            hc.setUsername(cfg.user());
            hc.setPassword(cfg.password());
            hc.setMaximumPoolSize(cfg.poolSize());
            hc.setMinimumIdle(Math.max(1, cfg.poolSize() / 2));
            hc.setConnectionTimeout(cfg.connectionTimeoutMs());
            hc.setPoolName("TownyWarPool");
            hc.setLeakDetectionThreshold(60_000);
            this.ds = new HikariDataSource(hc);
            initSchema();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка подключения к БД: " + e.getMessage(), e);
            return false;
        }
    }

    private void initSchema() throws SQLException {
        try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS wars (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  attacker_town VARCHAR(64) NOT NULL,
                  defender_town VARCHAR(64) NOT NULL,
                  attacker_points INT NOT NULL,
                  defender_points INT NOT NULL,
                  phase VARCHAR(24) NOT NULL,
                  declared_at BIGINT NOT NULL,
                  prep_ends_at BIGINT NOT NULL,
                  combat_started_at BIGINT NULL,
                  negotiation_ends_at BIGINT NULL,
                  ended_at BIGINT NULL,
                  winner_town VARCHAR(64) NULL,
                  outcome VARCHAR(32) NULL,
                  INDEX idx_towns (attacker_town, defender_town, ended_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS placed_flags (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  war_id BIGINT NOT NULL,
                  type VARCHAR(16) NOT NULL,
                  owner_town VARCHAR(64) NOT NULL,
                  placed_by VARCHAR(64) NULL,
                  target_town VARCHAR(64) NOT NULL,
                  world VARCHAR(64) NOT NULL,
                  x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
                  chunk_x INT NOT NULL, chunk_z INT NOT NULL,
                  durability INT NOT NULL,
                  max_durability INT NOT NULL,
                  placed_at BIGINT NOT NULL,
                  capture_finish_at BIGINT NULL,
                  paused_remaining_seconds INT NULL,
                  FOREIGN KEY (war_id) REFERENCES wars(id) ON DELETE CASCADE,
                  INDEX idx_war (war_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tributes (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  war_id BIGINT NOT NULL,
                  payer_town VARCHAR(64) NOT NULL,
                  receiver_town VARCHAR(64) NOT NULL,
                  percent INT NOT NULL DEFAULT 10,
                  next_at BIGINT NOT NULL,
                  active BOOLEAN NOT NULL DEFAULT 1,
                  FOREIGN KEY (war_id) REFERENCES wars(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS town_war_settings (
                  town VARCHAR(64) PRIMARY KEY,
                  max_concurrent_capture_flags INT NOT NULL DEFAULT 1
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS war_log (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  war_id BIGINT NULL,
                  ts BIGINT NOT NULL,
                  actor VARCHAR(64) NULL,
                  event VARCHAR(48) NOT NULL,
                  payload TEXT NULL,
                  INDEX idx_war_ts (war_id, ts)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS town_warshop_state (
                  town VARCHAR(64) PRIMARY KEY,
                  inspiration_ready_at BIGINT NOT NULL DEFAULT 0,
                  truce_ready_at BIGINT NOT NULL DEFAULT 0,
                  mercenaries_ready_at BIGINT NOT NULL DEFAULT 0,
                  inspiration_until BIGINT NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS warshop_truce (
                  war_id BIGINT PRIMARY KEY,
                  until_at BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS assault_tnt (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  war_id BIGINT NOT NULL,
                  owner_town VARCHAR(64) NOT NULL,
                  target_town VARCHAR(64) NOT NULL,
                  world VARCHAR(64) NOT NULL,
                  x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
                  chunk_x INT NOT NULL, chunk_z INT NOT NULL,
                  durability INT NOT NULL,
                  max_durability INT NOT NULL,
                  placed_at BIGINT NOT NULL,
                  detonate_at BIGINT NULL,
                  paused_remaining_seconds INT NULL,
                  FOREIGN KEY (war_id) REFERENCES wars(id) ON DELETE CASCADE,
                  INDEX idx_war (war_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);
        }

        // Идемпотентные миграции: добавляем колонку, если её ещё нет.
        try (Connection con = ds.getConnection()) {
            ensureColumn(con, "town_war_settings", "starting_points_bonus",
                    "ALTER TABLE town_war_settings ADD COLUMN starting_points_bonus INT NOT NULL DEFAULT 0");
            ensureColumn(con, "placed_flags", "placed_by",
                    "ALTER TABLE placed_flags ADD COLUMN placed_by VARCHAR(64) NULL");
        }
    }

    /**
     * Идемпотентная миграция: добавляем колонку, если её ещё нет.
     * MySQL 8 не поддерживает ADD COLUMN IF NOT EXISTS, поэтому проверяем
     * information_schema вручную.
     */
    private void ensureColumn(Connection con, String table, String column, String ddl) {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("ensureColumn check failed for " + table + "." + column
                    + ": " + e.getMessage());
        }
        try (Statement st = con.createStatement()) {
            st.executeUpdate(ddl);
            plugin.getLogger().info("DB migration: added " + table + "." + column);
        } catch (SQLException e) {
            // колонка уже есть — не ошибка
        }
    }

    public Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public void close() {
        if (ds != null && !ds.isClosed()) ds.close();
    }
}