package ru.example.townywar.war;

import org.jetbrains.annotations.Nullable;
import ru.example.townywar.api.WarSnapshot;

public final class War {

    private final long id;
    private final String attackerTown;
    private final String defenderTown;
    private int attackerPoints;
    private int defenderPoints;
    private WarPhase phase;
    private final long declaredAt;
    private final long prepEndsAt;
    private Long combatStartedAt;
    private Long negotiationEndsAt;
    private Long endedAt;
    private String winnerTown;
    private String outcome;

    public War(long id, String attackerTown, String defenderTown,
               int attackerPoints, int defenderPoints, WarPhase phase,
               long declaredAt, long prepEndsAt,
               @Nullable Long combatStartedAt, @Nullable Long negotiationEndsAt,
               @Nullable Long endedAt, @Nullable String winnerTown,
               @Nullable String outcome) {
        this.id = id;
        this.attackerTown = attackerTown;
        this.defenderTown = defenderTown;
        this.attackerPoints = attackerPoints;
        this.defenderPoints = defenderPoints;
        this.phase = phase;
        this.declaredAt = declaredAt;
        this.prepEndsAt = prepEndsAt;
        this.combatStartedAt = combatStartedAt;
        this.negotiationEndsAt = negotiationEndsAt;
        this.endedAt = endedAt;
        this.winnerTown = winnerTown;
        this.outcome = outcome;
    }

    public War withId(long newId) {
        return new War(newId, attackerTown, defenderTown, attackerPoints, defenderPoints,
                phase, declaredAt, prepEndsAt, combatStartedAt, negotiationEndsAt,
                endedAt, winnerTown, outcome);
    }

    /** Иммутабельный снимок для событий и API. */
    public WarSnapshot snapshot() {
        return new WarSnapshot(id, attackerTown, defenderTown,
                attackerPoints, defenderPoints, phase.name(),
                declaredAt, prepEndsAt, combatStartedAt, negotiationEndsAt,
                endedAt, winnerTown, outcome);
    }

    public boolean involves(String townName) {
        return attackerTown.equalsIgnoreCase(townName) || defenderTown.equalsIgnoreCase(townName);
    }

    @Nullable
    public String opposing(String townName) {
        if (attackerTown.equalsIgnoreCase(townName)) return defenderTown;
        if (defenderTown.equalsIgnoreCase(townName)) return attackerTown;
        return null;
    }

    @Nullable
    public WarSide sideOf(String townName) {
        if (attackerTown.equalsIgnoreCase(townName)) return WarSide.ATTACKER;
        if (defenderTown.equalsIgnoreCase(townName)) return WarSide.DEFENDER;
        return null;
    }

    public boolean isActive() { return phase != WarPhase.ENDED; }

    public long getId() { return id; }
    public String getAttackerTown() { return attackerTown; }
    public String getDefenderTown() { return defenderTown; }
    public int getAttackerPoints() { return attackerPoints; }
    public void setAttackerPoints(int v) { this.attackerPoints = v; }
    public int getDefenderPoints() { return defenderPoints; }
    public void setDefenderPoints(int v) { this.defenderPoints = v; }
    public WarPhase getPhase() { return phase; }
    public void setPhase(WarPhase p) { this.phase = p; }
    public long getDeclaredAt() { return declaredAt; }
    public long getPrepEndsAt() { return prepEndsAt; }
    public Long getCombatStartedAt() { return combatStartedAt; }
    public void setCombatStartedAt(Long v) { this.combatStartedAt = v; }
    public Long getNegotiationEndsAt() { return negotiationEndsAt; }
    public void setNegotiationEndsAt(Long v) { this.negotiationEndsAt = v; }
    public Long getEndedAt() { return endedAt; }
    public void setEndedAt(Long v) { this.endedAt = v; }
    public String getWinnerTown() { return winnerTown; }
    public void setWinnerTown(String v) { this.winnerTown = v; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String v) { this.outcome = v; }
}