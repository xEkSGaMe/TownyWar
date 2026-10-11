package ru.example.townywar.listener;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.CombatWindowManager;

import java.util.EnumSet;
import java.util.Set;

/**
 * В enemy-чанках во время боя запрещает <b>функциональное</b> взаимодействие
 * с блоками: открытие контейнеров, кнопки, рычаги, двери, верстаки, кровати
 * и т.д. Бросание зелий, еда, лук, фейерверки и прочее — разрешены.
 */
public final class InteractionLockListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Материалы, взаимодействие с которыми запрещено. */
    private static final Set<Material> BLACKLIST = EnumSet.noneOf(Material.class);

    static {
        for (Material m : Material.values()) {
            if (m.isLegacy()) continue;
            String n = m.name();

            // Двери, люки, калитки
            if (n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR")
                    || n.endsWith("_FENCE_GATE")) {
                BLACKLIST.add(m); continue;
            }
            // Кнопки
            if (n.endsWith("_BUTTON")) { BLACKLIST.add(m); continue; }
            // Плиты нажимные
            if (n.endsWith("_PRESSURE_PLATE")) { BLACKLIST.add(m); continue; }
            // Кровати
            if (n.endsWith("_BED")) { BLACKLIST.add(m); continue; }
            // Шалкеры
            if (n.endsWith("_SHULKER_BOX") || n.equals("SHULKER_BOX")) {
                BLACKLIST.add(m); continue;
            }
        }

        // Контейнеры
        addAll(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
                Material.ENDER_CHEST, Material.HOPPER, Material.DROPPER,
                Material.DISPENSER, Material.FURNACE, Material.BLAST_FURNACE,
                Material.SMOKER, Material.BREWING_STAND, Material.CRAFTER);

        // Верстаки и столы
        addAll(Material.CRAFTING_TABLE, Material.CARTOGRAPHY_TABLE,
                Material.SMITHING_TABLE, Material.LOOM, Material.STONECUTTER,
                Material.GRINDSTONE, Material.ANVIL, Material.CHIPPED_ANVIL,
                Material.DAMAGED_ANVIL, Material.ENCHANTING_TABLE,
                Material.FLETCHING_TABLE, Material.LECTERN);

        // Механизмы
        addAll(Material.LEVER, Material.REPEATER, Material.COMPARATOR,
                Material.DAYLIGHT_DETECTOR, Material.NOTE_BLOCK,
                Material.JUKEBOX, Material.BELL, Material.TRIPWIRE_HOOK,
                Material.RESPAWN_ANCHOR, Material.BEACON, Material.DRAGON_EGG,
                Material.FLOWER_POT, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
                Material.CAKE);
    }

    private static void addAll(Material... mats) {
        for (Material m : mats) if (m != null) BLACKLIST.add(m);
    }

    private final CombatWindowManager windowManager;

    public InteractionLockListener(TownyWarPlugin plugin, CombatWindowManager windowManager) {
        this.windowManager = windowManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!windowManager.isCombatWindowActive()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        var block = event.getClickedBlock();
        if (block == null) return;
        if (!windowManager.isEnemyChunkFor(player, block.getLocation())) return;

        // Установка блока — пропускаем, ей займётся BlockPlaceEvent.
        ItemStack item = event.getItem();
        if (item != null && item.getType().isBlock()) return;

        // Разрешаем всё, что не в чёрном списке (зелья, еда, лук и т.д.).
        if (!BLACKLIST.contains(block.getType())) return;

        event.setCancelled(true);
        player.sendActionBar(MM.deserialize(
                "<red>В чанках противника нельзя взаимодействовать с этим блоком.</red>"));
    }
}