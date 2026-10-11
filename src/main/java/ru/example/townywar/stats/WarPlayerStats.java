package ru.example.townywar.stats;

public record WarPlayerStats(
        String playerName,
        int kills,
        int deaths,
        int flagsPlaced,
        int flagsBroken,
        int gapples,
        int pearls
) { }