package ru.example.townywar.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private final Logger logger;

    private int minTownAgeDays, preparationHours;
    private ZoneId zoneId;
    private int windowStartMinutes, windowEndMinutes;
    private int defaultPoints, deathMayor, deathResident;
    private int minTotalPoints, maxTotalPoints;
    private int captureDurability, captureSeconds, captureMinY, captureMaxY, captureDefaultMaxPerTown;
    private int surrenderDurability, surrenderSeconds;
    private int negotiationMinutes;
    private int tributePercent, tributeHour, tributeMinute;
    private int glowRadius;
    private List<String> blockedCommands;

    private int inspirationCost, inspirationCooldownMin, inspirationDurationMin, inspirationBonusPercent;
    private int truceCost, truceCooldownMin, truceDurationMin;
    private int mercenariesCost, mercenariesCooldownMin, mercenariesPoints;

    // Assault TNT
    private int assaultTntDurability, assaultTntFuseSeconds, assaultTntMaxPerChunk;
    private float assaultTntPower;

    private DatabaseConfig databaseConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        var c = plugin.getConfig();
        this.minTownAgeDays = clamp(c.getInt("war.declare.min-town-age-days", 7), 0, 365);
        this.preparationHours = clamp(c.getInt("war.preparation-hours", 48), 1, 24 * 14);

        String tz = c.getString("war.combat.timezone", "Europe/Moscow");
        ZoneId parsed;
        try { parsed = ZoneId.of(tz); }
        catch (Exception e) { logger.warning("Таймзона " + tz + " неверная, Europe/Moscow."); parsed = ZoneId.of("Europe/Moscow"); }
        this.zoneId = parsed;

        this.windowStartMinutes = parseHm(c.getString("war.combat.window.start", "16:00"), 16 * 60);
        this.windowEndMinutes = parseHm(c.getString("war.combat.window.end", "20:30"), 20 * 60 + 30);
        if (windowEndMinutes <= windowStartMinutes) windowEndMinutes = Math.min(24 * 60 - 1, windowStartMinutes + 60);

        this.defaultPoints = clamp(c.getInt("war.points.default", 250), 1, 1_000_000);
        this.deathMayor = clamp(c.getInt("war.points.death-mayor", 10), 0, 1_000_000);
        this.deathResident = clamp(c.getInt("war.points.death-resident", 5), 0, 1_000_000);
        this.minTotalPoints = clamp(c.getInt("war.points.min-total", 1), 1, 1_000_000);
        this.maxTotalPoints = clamp(c.getInt("war.points.max-total", 100_000),
                minTotalPoints, 10_000_000);

        this.captureDurability = clamp(c.getInt("war.capture.flag-durability", 30), 1, 10_000);
        this.captureSeconds = clamp(c.getInt("war.capture.capture-seconds", 120), 5, 86_400);
        this.captureMinY = clamp(c.getInt("war.capture.min-y", 0), -64, 320);
        this.captureMaxY = clamp(c.getInt("war.capture.max-y", 96), -64, 320);
        if (captureMaxY <= captureMinY) captureMaxY = Math.min(320, captureMinY + 1);
        this.captureDefaultMaxPerTown = clamp(c.getInt("war.capture.default-max-concurrent-per-town", 1), 1, 64);

        this.surrenderDurability = clamp(c.getInt("war.surrender.flag-durability", 150), 1, 100_000);
        this.surrenderSeconds = clamp(c.getInt("war.surrender.surrender-seconds", 1800), 5, 86_400 * 7);
        this.negotiationMinutes = clamp(c.getInt("war.negotiation.window-minutes", 5), 1, 60 * 24);
        this.tributePercent = clamp(c.getInt("war.tribute.percent", 10), 1, 100);
        this.tributeHour = clamp(c.getInt("war.tribute.hour", 13), 0, 23);
        this.tributeMinute = clamp(c.getInt("war.tribute.minute", 0), 0, 59);
        this.glowRadius = clamp(c.getInt("war.glow.radius", 48), 8, 256);

        this.inspirationCost = clamp(c.getInt("warshop.inspiration.cost", 10000), 0, 100_000_000);
        this.inspirationCooldownMin = clamp(c.getInt("warshop.inspiration.cooldown-minutes", 60), 1, 60 * 24 * 7);
        this.inspirationDurationMin = clamp(c.getInt("warshop.inspiration.duration-minutes", 10), 1, 60 * 24);
        this.inspirationBonusPercent = clamp(c.getInt("warshop.inspiration.damage-bonus-percent", 3), 1, 1000);

        this.truceCost = clamp(c.getInt("warshop.truce.cost", 15000), 0, 100_000_000);
        this.truceCooldownMin = clamp(c.getInt("warshop.truce.cooldown-minutes", 60), 1, 60 * 24 * 7);
        this.truceDurationMin = clamp(c.getInt("warshop.truce.duration-minutes", 10), 1, 60 * 24);

        this.mercenariesCost = clamp(c.getInt("warshop.mercenaries.cost", 25000), 0, 100_000_000);
        this.mercenariesCooldownMin = clamp(c.getInt("warshop.mercenaries.cooldown-minutes", 90), 1, 60 * 24 * 7);
        this.mercenariesPoints = clamp(c.getInt("warshop.mercenaries.points", 50), 1, 100_000);

        this.assaultTntDurability = clamp(c.getInt("war.assault-tnt.durability", 30), 1, 10_000);
        this.assaultTntFuseSeconds = clamp(c.getInt("war.assault-tnt.fuse-seconds", 30), 1, 600);
        this.assaultTntMaxPerChunk = clamp(c.getInt("war.assault-tnt.max-per-chunk", 8), 1, 64);
        double p = c.getDouble("war.assault-tnt.explosion-power", 4.0);
        if (p < 1.0) p = 1.0;
        if (p > 20.0) p = 20.0;
        this.assaultTntPower = (float) p;

        List<String> bc = c.getStringList("war.blocked-commands");
        if (bc.isEmpty()) {
            bc = List.of(
                    "delete", "merge", "withdraw", "unclaim", "claim", "rename",
                    "set name", "set tag", "set mayor", "set homeblock", "set spawn",
                    "toggle pvp", "toggle explosion");
        }
        this.blockedCommands = bc.stream()
                .map(s -> s == null ? "" : s.toLowerCase(Locale.ROOT).trim())
                .filter(s -> !s.isEmpty())
                .toList();

        this.databaseConfig = new DatabaseConfig(
                c.getString("database.host", "localhost"),
                c.getInt("database.port", 3306),
                c.getString("database.name", "townywar"),
                c.getString("database.user", "root"),
                c.getString("database.password", ""),
                clamp(c.getInt("database.pool-size", 10), 1, 32),
                c.getBoolean("database.use-ssl", false),
                clamp(c.getInt("database.connection-timeout-ms", 10_000), 1_000, 60_000));
    }

    private static int parseHm(String s, int fallback) {
        if (s == null) return fallback;
        String[] parts = s.split(":");
        if (parts.length != 2) return fallback;
        try {
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) return fallback;
            return h * 60 + m;
        } catch (NumberFormatException e) { return fallback; }
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    public int getMinTownAgeDays() { return minTownAgeDays; }
    public int getPreparationHours() { return preparationHours; }
    public ZoneId getZoneId() { return zoneId; }
    public int getWindowStartMinutes() { return windowStartMinutes; }
    public int getWindowEndMinutes() { return windowEndMinutes; }
    public int getDefaultPoints() { return defaultPoints; }
    public int getDeathMayor() { return deathMayor; }
    public int getDeathResident() { return deathResident; }
    public int getMinTotalPoints() { return minTotalPoints; }
    public int getMaxTotalPoints() { return maxTotalPoints; }
    public int getCaptureDurability() { return captureDurability; }
    public int getCaptureSeconds() { return captureSeconds; }
    public int getCaptureMinY() { return captureMinY; }
    public int getCaptureMaxY() { return captureMaxY; }
    public int getCaptureDefaultMaxPerTown() { return captureDefaultMaxPerTown; }
    public int getSurrenderDurability() { return surrenderDurability; }
    public int getSurrenderSeconds() { return surrenderSeconds; }
    public int getNegotiationMinutes() { return negotiationMinutes; }
    public int getTributePercent() { return tributePercent; }
    public int getTributeHour() { return tributeHour; }
    public int getTributeMinute() { return tributeMinute; }
    public int getGlowRadius() { return glowRadius; }
    public List<String> getBlockedCommands() { return blockedCommands; }

    public int getInspirationCost() { return inspirationCost; }
    public int getInspirationCooldownMin() { return inspirationCooldownMin; }
    public int getInspirationDurationMin() { return inspirationDurationMin; }
    public int getInspirationBonusPercent() { return inspirationBonusPercent; }
    public int getTruceCost() { return truceCost; }
    public int getTruceCooldownMin() { return truceCooldownMin; }
    public int getTruceDurationMin() { return truceDurationMin; }
    public int getMercenariesCost() { return mercenariesCost; }
    public int getMercenariesCooldownMin() { return mercenariesCooldownMin; }
    public int getMercenariesPoints() { return mercenariesPoints; }

    // Assault TNT
    public int getAssaultTntDurability() { return assaultTntDurability; }
    public int getAssaultTntFuseSeconds() { return assaultTntFuseSeconds; }
    public int getAssaultTntMaxPerChunk() { return assaultTntMaxPerChunk; }
    public float getAssaultTntPower() { return assaultTntPower; }

    public DatabaseConfig getDatabaseConfig() { return databaseConfig; }

    public record DatabaseConfig(String host, int port, String name, String user, String password,
                                 int poolSize, boolean useSsl, int connectionTimeoutMs) { }
}