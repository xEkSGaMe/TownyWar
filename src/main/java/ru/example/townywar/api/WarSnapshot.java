package ru.example.townywar.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record WarSnapshot(
        long id,
        @NotNull String attackerTown,
        @NotNull String defenderTown,
        int attackerPoints,
        int defenderPoints,
        @NotNull String phase,
        long declaredAt,
        long prepEndsAt,
        @Nullable Long combatStartedAt,
        @Nullable Long negotiationEndsAt,
        @Nullable Long endedAt,
        @Nullable String winnerTown,
        @Nullable String outcome
) {
    public boolean involves(@NotNull String town) {
        return attackerTown.equalsIgnoreCase(town) || defenderTown.equalsIgnoreCase(town);
    }

    @Nullable
    public String opposing(@NotNull String town) {
        if (attackerTown.equalsIgnoreCase(town)) return defenderTown;
        if (defenderTown.equalsIgnoreCase(town)) return attackerTown;
        return null;
    }
}