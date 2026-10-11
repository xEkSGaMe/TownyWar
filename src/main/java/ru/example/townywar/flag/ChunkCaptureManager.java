package ru.example.townywar.flag;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.townywar.TownyWarPlugin;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Защита от дюпов при передаче чанков: лочим чанк на время передачи,
 * чтобы два флага не захватили его одновременно.
 */
public final class ChunkCaptureManager {

    private static final ConcurrentHashMap<String, Long> LOCKS = new ConcurrentHashMap<>();
    private static final long LOCK_TTL_MS = 5_000L;

    private ChunkCaptureManager() { }

    public static boolean tryLock(String world, int chunkX, int chunkZ) {
        long now = System.currentTimeMillis();
        String key = world + ":" + chunkX + ":" + chunkZ;
        Long prev = LOCKS.putIfAbsent(key, now);
        if (prev != null && now - prev < LOCK_TTL_MS) return false;
        LOCKS.put(key, now);
        return true;
    }

    public static void unlock(String world, int chunkX, int chunkZ) {
        LOCKS.remove(world + ":" + chunkX + ":" + chunkZ);
    }

    public static void cleanup() {
        long now = System.currentTimeMillis();
        LOCKS.entrySet().removeIf(e -> now - e.getValue() > LOCK_TTL_MS);
    }
}