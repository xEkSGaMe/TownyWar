package ru.example.townywar.flag;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class PlacedFlag {

    private final UUID flagId;
    private final long warId;
    private final FlagType type;
    private final String ownerTown;
    private final String placedBy;   // ← NEW (может быть null для legacy-флагов)
    private final String targetTown, world;
    private final int x, y, z, chunkX, chunkZ;
    private int durability;
    private final int maxDurability;
    private final long placedAt;
    private Long captureFinishAt;
    private Integer pausedRemainingSeconds;
    private UUID displayEntityId;

    /**
     * Ключ последнего отрисованного текста на TextDisplay. Сравнивается в
     * {@link FlagDisplay#update(PlacedFlag)} перед отправкой metadata-пакета,
     * чтобы не спамить клиентам одинаковый текст каждую секунду.
     * <p>
     * Формат: {@code "<icon>:<secs>:<dur>/<maxDur>"}, где
     * <ul>
     *     <li>{@code icon} — {@code "P"} для паузы ({@code ⏸}) или {@code "R"} для бега ({@code ⏳});</li>
     *     <li>{@code secs} — оставшиеся секунды таймера, округлённые;</li>
     *     <li>{@code dur}/{@code maxDur} — текущая и максимальная прочность.</li>
     * </ul>
     * Ключ отражает то, что видит игрок, а не сырые миллисекунды.
     */
    private String lastRenderedKey;

    public PlacedFlag(UUID flagId, long warId, FlagType type,
                      String ownerTown, String placedBy, String targetTown,
                      String world, int x, int y, int z, int chunkX, int chunkZ,
                      int durability, int maxDurability, long placedAt,
                      @Nullable Long captureFinishAt, @Nullable Integer pausedRemainingSeconds) {
        this.flagId = flagId; this.warId = warId; this.type = type;
        this.ownerTown = ownerTown; this.placedBy = placedBy;
        this.targetTown = targetTown; this.world = world;
        this.x = x; this.y = y; this.z = z;
        this.chunkX = chunkX; this.chunkZ = chunkZ;
        this.durability = durability; this.maxDurability = maxDurability;
        this.placedAt = placedAt;
        this.captureFinishAt = captureFinishAt;
        this.pausedRemainingSeconds = pausedRemainingSeconds;
    }

    public boolean isPaused() { return captureFinishAt == null; }

    public UUID getFlagId() { return flagId; }
    public long getWarId() { return warId; }
    public FlagType getType() { return type; }
    public String getOwnerTown() { return ownerTown; }
    public String getPlacedBy() { return placedBy; }
    public String getTargetTown() { return targetTown; }
    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public int getDurability() { return durability; }
    public void setDurability(int v) { this.durability = v; }
    public int getMaxDurability() { return maxDurability; }
    public long getPlacedAt() { return placedAt; }
    public Long getCaptureFinishAt() { return captureFinishAt; }
    public void setCaptureFinishAt(Long v) { this.captureFinishAt = v; }
    public Integer getPausedRemainingSeconds() { return pausedRemainingSeconds; }
    public void setPausedRemainingSeconds(Integer v) { this.pausedRemainingSeconds = v; }
    public UUID getDisplayEntityId() { return displayEntityId; }
    public void setDisplayEntityId(UUID v) { this.displayEntityId = v; }
    public String getLastRenderedKey() { return lastRenderedKey; }
    public void setLastRenderedKey(String key) { this.lastRenderedKey = key; }
}