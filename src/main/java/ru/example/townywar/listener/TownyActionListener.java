package ru.example.townywar.listener;

import com.palmergames.bukkit.towny.event.actions.TownyBuildEvent;
import com.palmergames.bukkit.towny.event.actions.TownyDestroyEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.CombatWindowManager;
import ru.example.townywar.war.WarManager;

import java.util.EnumSet;
import java.util.Set;

public final class TownyActionListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /**
     * Блоки, которые нельзя ломать/ставить в чанках противника во время боя.
     * Список из ТЗ: верстаки, бочки, двери (все виды), кнопки, рамки, картины.
     * Стойки для брони обрабатываются отдельно в ArmorStandListener.
     */
    private static final Set<Material> BLACKLIST = EnumSet.noneOf(Material.class);

    static {
        for (Material m : Material.values()) {
            if (m.isLegacy()) continue;
            String n = m.name();
            if (n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR") || n.endsWith("_FENCE_GATE")) {
                BLACKLIST.add(m);
                continue;
            }
            if (n.endsWith("_BUTTON")) {
                BLACKLIST.add(m);
                continue;
            }
            if (n.equals("ITEM_FRAME") || n.equals("GLOW_ITEM_FRAME") || n.equals("PAINTING")) {
                BLACKLIST.add(m);
            }
        }
        BLACKLIST.add(Material.CRAFTING_TABLE);
        BLACKLIST.add(Material.BARREL);
    }

    private final TownyWarPlugin plugin;
    private final WarManager warManager;
    private final CombatWindowManager windowManager;

    public TownyActionListener(TownyWarPlugin plugin, WarManager warManager, CombatWindowManager windowManager) {
        this.plugin = plugin;
        this.warManager = warManager;
        this.windowManager = windowManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDestroy(TownyDestroyEvent event) {
        if (!windowManager.isCombatWindowActive()) return;

        Player player = event.getPlayer();
        if (player == null) return;
        if (!player.hasPermission("townywar.breakblocks")) return;
        if (!windowManager.isEnemyChunkFor(player, event.getLocation())) return;

        Material mat = event.getMaterial();
        if (mat != null && BLACKLIST.contains(mat)) {
            if (!event.isCancelled()) {
                event.setCancelled(true);
            }
            player.sendActionBar(MM.deserialize("<red>Этот блок нельзя ломать в чанках противника.</red>"));
            return;
        }

        // Это чанк врага в боевом окне, у игрока есть право — снимаем запрет Towny.
        if (event.isCancelled()) {
            event.setCancelled(false);
            plugin.getWarLogRepository().log(null, player.getName(), "DESTROY_OVERRIDE",
                    "mat=" + (mat == null ? "?" : mat.name())
                            + ";x=" + event.getLocation().getBlockX()
                            + ";y=" + event.getLocation().getBlockY()
                            + ";z=" + event.getLocation().getBlockZ());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBuild(TownyBuildEvent event) {
        if (!windowManager.isCombatWindowActive()) return;

        Player player = event.getPlayer();
        if (player == null) return;
        if (!player.hasPermission("townywar.placeblocks")) return;
        if (!windowManager.isEnemyChunkFor(player, event.getLocation())) return;

        Material mat = event.getMaterial();
        if (mat != null && BLACKLIST.contains(mat)) {
            if (!event.isCancelled()) {
                event.setCancelled(true);
            }
            player.sendActionBar(MM.deserialize("<red>Этот блок нельзя ставить в чанках противника.</red>"));
            return;
        }

        if (event.isCancelled()) {
            event.setCancelled(false);
            plugin.getWarLogRepository().log(null, player.getName(), "BUILD_OVERRIDE",
                    "mat=" + (mat == null ? "?" : mat.name())
                            + ";x=" + event.getLocation().getBlockX()
                            + ";y=" + event.getLocation().getBlockY()
                            + ";z=" + event.getLocation().getBlockZ());
        }
    }
}