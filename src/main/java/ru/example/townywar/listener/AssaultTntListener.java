package ru.example.townywar.listener;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.assault.AssaultTnt;
import ru.example.townywar.assault.AssaultTntItemFactory;
import ru.example.townywar.assault.AssaultTntManager;

public final class AssaultTntListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final TownyWarPlugin plugin;
    private final AssaultTntManager manager;

    public AssaultTntListener(TownyWarPlugin plugin, AssaultTntManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Правый клик:
     *   • flint&steel по нашему TNT → cancel (не даём ванильно поджечь);
     *   • наш предмет-TNT → ставим вручную (отменяем ваниль).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        ItemStack item = event.getItem();

        // Защита от поджога
        if (item != null && (item.getType() == Material.FLINT_AND_STEEL
                || item.getType() == Material.FIRE_CHARGE)
                && manager.isTntBlock(clicked)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(MM.deserialize(
                    "<red>Штурмовой ТНТ нельзя поджечь вручную.</red>"));
            return;
        }

        if (!AssaultTntItemFactory.isTnt(plugin, item)) return;

        // Своё размещение — всегда cancel у ванилы.
        event.setCancelled(true);

        BlockFace face = event.getBlockFace();
        Block target = clicked.getRelative(face);
        if (!target.getType().isAir() && target.getType() != Material.SHORT_GRASS
                && target.getType() != Material.TALL_GRASS) {
            event.getPlayer().sendActionBar(MM.deserialize("<red>Здесь некуда поставить ТНТ.</red>"));
            return;
        }

        Player player = event.getPlayer();
        boolean placed = manager.place(player, target);
        if (placed) decrementFromHand(player, event.getHand());
    }

    private void decrementFromHand(Player player, EquipmentSlot hand) {
        if (hand == null) return;
        var inv = player.getInventory();
        boolean offHand = hand == EquipmentSlot.OFF_HAND;
        ItemStack stack = offHand ? inv.getItemInOffHand() : inv.getItemInMainHand();
        if (stack == null || stack.getType().isAir()) return;
        if (!AssaultTntItemFactory.isTnt(plugin, stack)) return;

        int newAmount = stack.getAmount() - 1;
        if (newAmount <= 0) {
            if (offHand) inv.setItemInOffHand(null);
            else inv.setItemInMainHand(null);
        } else {
            stack.setAmount(newAmount);
            if (offHand) inv.setItemInOffHand(stack);
            else inv.setItemInMainHand(stack);
        }
    }

    /**
     * Как флаг: событие отменяем ВСЕГДА, урон — только воюющим.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        AssaultTnt t = manager.getTntAt(block);
        if (t == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!manager.canPlayerBreakTnt(player, t)) {
            if (player != null) {
                player.sendActionBar(MM.deserialize(
                        "<red>ТНТ можно ломать только во время боевого окна и только воюющим.</red>"));
            }
            return;
        }
        manager.handleBreak(player, block);
    }

    /** Огонь / лава / spread не должны поджигать наш TNT. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        if (block != null && manager.isTntBlock(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        manager.spawnDisplaysInChunk(event.getChunk());
    }
}