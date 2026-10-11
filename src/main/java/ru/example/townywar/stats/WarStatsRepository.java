package ru.example.townywar.stats;

import ru.example.townywar.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WarStatsRepository {

    private static final Logger LOG = Logger.getLogger("TownyWar");
    private final Database db;

    public WarStatsRepository(Database db) { this.db = db; }

    /**
     * Агрегирует статистику игроков за конкретную войну из war_log.
     * Все события уже пишутся туда по ходу боя — здесь только read.
     * <p>
     * События, которые учитываются:
     * <ul>
     *   <li>{@code PVP_KILL} — actor = убийца, только те убийства, что снесли очки;</li>
     *   <li>{@code PVP_DEATH} — actor = жертва (зеркально PVP_KILL);</li>
     *   <li>{@code FLAG_COMPLETED} — actor = поставивший флаг, событие логируется
     *       только когда флаг довёл до захвата/капитуляции;</li>
     *   <li>{@code FLAG_BROKEN_ENEMY} — actor = сломавший, только вражеские флаги;</li>
     *   <li>{@code GAPPLE_EATEN} — actor = съевший;</li>
     *   <li>{@code ENDER_PEARL_USED} — actor = бросивший.</li>
     * </ul>
     */
    public List<WarPlayerStats> loadForWar(long warId) {
        String sql = """
                SELECT actor,
                  SUM(CASE WHEN event='PVP_KILL'          THEN 1 ELSE 0 END) AS kills,
                  SUM(CASE WHEN event='PVP_DEATH'         THEN 1 ELSE 0 END) AS deaths,
                  SUM(CASE WHEN event='FLAG_COMPLETED'    THEN 1 ELSE 0 END) AS flags_placed,
                  SUM(CASE WHEN event='FLAG_BROKEN_ENEMY' THEN 1 ELSE 0 END) AS flags_broken,
                  SUM(CASE WHEN event='GAPPLE_EATEN'      THEN 1 ELSE 0 END) AS gapples,
                  SUM(CASE WHEN event='ENDER_PEARL_USED'  THEN 1 ELSE 0 END) AS pearls
                FROM war_log
                WHERE war_id = ? AND actor IS NOT NULL AND actor <> 'SYSTEM'
                GROUP BY actor
                HAVING kills + deaths + flags_placed + flags_broken + gapples + pearls > 0
                """;

        List<WarPlayerStats> out = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, warId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new WarPlayerStats(
                            rs.getString("actor"),
                            rs.getInt("kills"),
                            rs.getInt("deaths"),
                            rs.getInt("flags_placed"),
                            rs.getInt("flags_broken"),
                            rs.getInt("gapples"),
                            rs.getInt("pearls")));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "war player stats load failed (warId=" + warId + ")", e);
        }

        // Сортировка по вкладу: kills desc, тай-брейк — детерминированный.
        out.sort(Comparator
                .comparingInt(WarPlayerStats::kills).reversed()
                .thenComparing(WarPlayerStats::playerName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }
}