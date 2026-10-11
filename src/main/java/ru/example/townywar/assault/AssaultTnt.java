package ru.example.townywar.assault;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class AssaultTnt {

    private final UUID tntId;
    private final long warId;
    private final String ownerTown, targetTown, world;
    private final int x, y, z, chunkX, chunkZ;
    private int durability;
    private final int maxDurability;
    private final long placedAt;
    private Long detonateAt;
    private Integer pausedRemainingSeconds;
    private UUID displayEntityId;
    private String lastRenderedKey;

    public AssaultTnt(UUID tntId, long warId,
                      String ownerTown, String targetTown,
                      String world, int x, int y, int z, int chunkX, int chunkZ,
                      int durability, int maxDurability, long placedAt,
                      @Nullable Long detonateAt, @Nullable Integer pausedRemainingSeconds) {
        this.tntId = tntId; this.warId = warId;
        this.ownerTown = ownerTown; this.targetTown = targetTown; this.world = world;
        this.x = x; this.y = y; this.z = z;
        this.chunkX = chunkX; this.chunkZ = chunkZ;
        this.durability = durability; this.maxDurability = maxDurability;
        this.placedAt = placedAt;
        this.detonateAt = detonateAt;
        this.pausedRemainingSeconds = pausedRemainingSeconds;
    }

    public boolean isPaused() { return detonateAt == null; }

    public UUID getTntId() { return tntId; }
    public long getWarId() { return warId; }
    public String getOwnerTown() { return ownerTown; }
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
    public Long getDetonateAt() { return detonateAt; }
    public void setDetonateAt(Long v) { this.detonateAt = v; }
    public Integer getPausedRemainingSeconds() { return pausedRemainingSeconds; }
    public void setPausedRemainingSeconds(Integer v) { this.pausedRemainingSeconds = v; }
    public UUID getDisplayEntityId() { return displayEntityId; }
    public void setDisplayEntityId(UUID v) { this.displayEntityId = v; }
    public String getLastRenderedKey() { return lastRenderedKey; }
    public void setLastRenderedKey(String key) { this.lastRenderedKey = key; }
}