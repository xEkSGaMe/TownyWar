package ru.example.townywar.database;

import org.bukkit.plugin.java.JavaPlugin;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarPhase;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WarRepository {

    private static final Logger LOG = Logger.getLogger("TownyWar");
    private static final long FLUSH_TIMEOUT_SECONDS = 5L;

    private final Database db;
    private final JavaPlugin plugin;

    /**
     * A3: сериализация апдейтов по warId.
     * Один single-thread-executor на войну. Все update-задачи одной войны
     * выполняются строго в порядке постановки, что устраняет гонку между
     * парными апдейтами (например, points-change → phase-change при
     * переходе в NEGOTIATION).
     */
    private final Map<Long, ExecutorService> executors = new ConcurrentHashMap<>();

    public WarRepository(Database db, JavaPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
    }

    private ExecutorService executorFor(long warId) {
        return executors.computeIfAbsent(warId, id -> Executors.newSingleThreadExecutor(r -> {
            Thread th = new Thread(r, "TownyWar-DB-War-" + id);
            th.setDaemon(true);
            return th;
        }));
    }

    public War insert(War w) throws SQLException {
        String sql = "INSERT INTO wars (attacker_town, defender_town, attacker_points, defender_points, phase, declared_at, prep_ends_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, w.getAttackerTown());
            ps.setString(2, w.getDefenderTown());
            ps.setInt(3, w.getAttackerPoints());
            ps.setInt(4, w.getDefenderPoints());
            ps.setString(5, w.getPhase().name());
            ps.setLong(6, w.getDeclaredAt());
            ps.setLong(7, w.getPrepEndsAt());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return w.withId(rs.getLong(1));
            }
        }
        throw new SQLException("Не удалось вставить войну");
    }

    /**
     * A3: асинхронный апдейт с сериализацией по warId.
     * Все апдейты одной войны идут последовательно в одном потоке,
     * поэтому out-of-order перезапись фазы/очков исключена.
     */
    public void update(War w) {
        if (!plugin.isEnabled()) return;
        final long id = w.getId();
        final int ap = w.getAttackerPoints();
        final int dp = w.getDefenderPoints();
        final String phase = w.getPhase().name();
        final Long combatStart = w.getCombatStartedAt();
        final Long negEnd = w.getNegotiationEndsAt();
        final Long endedAt = w.getEndedAt();
        final String winner = w.getWinnerTown();
        final String outcome = w.getOutcome();

        try {
            executorFor(id).execute(() -> {
                String sql = "UPDATE wars SET attacker_points=?, defender_points=?, phase=?, combat_started_at=?, negotiation_ends_at=?, ended_at=?, winner_town=?, outcome=? WHERE id=?";
                try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, ap);
                    ps.setInt(2, dp);
                    ps.setString(3, phase);
                    setNullableLong(ps, 4, combatStart);
                    setNullableLong(ps, 5, negEnd);
                    setNullableLong(ps, 6, endedAt);
                    ps.setString(7, winner);
                    ps.setString(8, outcome);
                    ps.setLong(9, id);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    LOG.log(Level.WARNING, "war async update failed (id=" + id + ")", e);
                }
            });
        } catch (RejectedExecutionException e) {
            // Executor уже закрыт (endWar или shutdown) — апдейт не нужен.
            LOG.log(Level.FINE, "war update rejected for closed executor (id=" + id + ")");
        }
    }

    /**
     * A3: освобождает executor войны после endWar. Уже стоящие в очереди
     * апдейты доработают, новые — отклонятся.
     */
    public void releaseExecutor(long warId) {
        ExecutorService ex = executors.remove(warId);
        if (ex != null) ex.shutdown();
    }

    /**
     * A3: shutdown-путь.
     * Порядок важен: сначала await pending-тасков (чтобы async-апдейты не
     * перезаписали sync-запись), затем updateSync финального состояния,
     * затем закрытие executor'ов.
     */
    public void flushAndShutdown(Collection<War> active) {
        List<ExecutorService> toShutdown = new ArrayList<>(executors.values());
        executors.clear();
        for (ExecutorService ex : toShutdown) ex.shutdown();
        for (ExecutorService ex : toShutdown) {
            try {
                if (!ex.awaitTermination(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    ex.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ex.shutdownNow();
            }
        }
        for (War w : active) updateSync(w);
    }

    public void updateSync(War w) {
        String sql = "UPDATE wars SET attacker_points=?, defender_points=?, phase=?, combat_started_at=?, negotiation_ends_at=?, ended_at=?, winner_town=?, outcome=? WHERE id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, w.getAttackerPoints());
            ps.setInt(2, w.getDefenderPoints());
            ps.setString(3, w.getPhase().name());
            setNullableLong(ps, 4, w.getCombatStartedAt());
            setNullableLong(ps, 5, w.getNegotiationEndsAt());
            setNullableLong(ps, 6, w.getEndedAt());
            ps.setString(7, w.getWinnerTown());
            ps.setString(8, w.getOutcome());
            ps.setLong(9, w.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "war sync update failed (id=" + w.getId() + ")", e);
        }
    }

    public List<War> loadActive() {
        List<War> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM wars WHERE phase <> 'ENDED'");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { LOG.log(Level.WARNING, "war load failed", e); }
        return list;
    }

    private static War map(ResultSet rs) throws SQLException {
        return new War(rs.getLong("id"), rs.getString("attacker_town"), rs.getString("defender_town"),
                rs.getInt("attacker_points"), rs.getInt("defender_points"),
                WarPhase.valueOf(rs.getString("phase")),
                rs.getLong("declared_at"), rs.getLong("prep_ends_at"),
                nullableLong(rs, "combat_started_at"), nullableLong(rs, "negotiation_ends_at"),
                nullableLong(rs, "ended_at"), rs.getString("winner_town"), rs.getString("outcome"));
    }

    private static Long nullableLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col); return rs.wasNull() ? null : v;
    }

    private static void setNullableLong(PreparedStatement ps, int idx, Long v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.BIGINT); else ps.setLong(idx, v);
    }
}