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
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.flag.FlagItemFactory;
import ru.example.townywar.flag.FlagManager;
import ru.example.townywar.flag.FlagType;
import ru.example.townywar.flag.PlacedFlag;

public final class FlagListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final TownyWarPlugin plugin;
    private final FlagManager flagManager;

    public FlagListener(TownyWarPlugin plugin, FlagManager flagManager) {
        this.plugin = plugin;
        this.flagManager = flagManager;
    }

    /**
     * B2: установка флага. Если событие уже отменено другим плагином
     * (WorldGuard, регион-плагин, античит) — не имеем права ставить флаг
     * в обход чужого запрета. Поэтому ignoreCancelled = true.
     * <p>
     * Работает и для main hand, и для offhand. Раньше здесь стоял early-return
     * {@code if (getHand() != HAND) return;}, из-за чего флаг в левой руке не
     * распознавался, событие не отменялось, и ванилла ставила обычный баннер
     * как декор-блок. Теперь оба события обрабатываются: первое по факту
     * плейсит флаг, второе отсекается проверкой баннера внутри
     * {@link FlagManager#place}.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!FlagItemFactory.isFlag(plugin, item)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Отменяем ванильное размещение баннера как блока. Делаем это до плейса,
        // чтобы клиент не получил обычный баннер даже если place() ниже откажет.
        event.setCancelled(true);

        Player player = event.getPlayer();
        FlagType type = FlagItemFactory.getType(plugin, item);
        if (type == null) return;

        Block target = block.getRelative(BlockFace.UP);
        if (!target.getType().isAir() && target.getType() != Material.SHORT_GRASS
                && target.getType() != Material.TALL_GRASS) {
            target = block;
        }

        boolean placed = flagManager.place(player, target, type);
        if (placed) {
            decrementFromHand(player, event.getHand());
        }
    }

    /**
     * Уменьшает стак флага в той руке, откуда пришёл клик.
     * При полном расходе — очищает слот через {@code null} (надёжнее, чем setAmount(0)).
     */
    private void decrementFromHand(Player player, EquipmentSlot hand) {
        if (hand == null) return;

        var inv = player.getInventory();
        boolean offHand = hand == EquipmentSlot.OFF_HAND;

        ItemStack stack = offHand ? inv.getItemInOffHand() : inv.getItemInMainHand();
        if (stack == null || stack.getType().isAir()) return;
        if (!FlagItemFactory.isFlag(plugin, stack)) return;

        int newAmount = stack.getAmount() - 1;
        if (newAmount <= 0) {
            if (offHand) inv.setItemInOffHand(null);
            else inv.setItemInMainHand(null);
        } else {
            stack.setAmount(newAmount);
            // Явная переустановка слота — гарантирует синк с клиентом.
            if (offHand) inv.setItemInOffHand(stack);
            else inv.setItemInMainHand(stack);
        }
    }

    /**
     * A4 (ревизия): баннер флага — защищённый блок.
     *   • событие отменяем ВСЕГДА, независимо от того, кто бьёт и когда —
     *     иначе trust/permission в Towny позволит снести баннер как обычный
     *     блок и рассинхронизирует мир с БД плагина;
     *   • урон наносит только игрок, прошедший canPlayerBreakFlag —
     *     открытое окно + COMBAT + одна из двух воюющих сторон;
     *   • вне окна/фазы флаг неприкосновенен даже для воюющих (таймеры
     *     стоят на паузе, целостность важнее).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        PlacedFlag f = flagManager.getFlagAt(block);
        if (f == null) return;

        // Всегда отменяем — баннер трогает только наша механика.
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!flagManager.canPlayerBreakFlag(player, f)) {
            // Воюющий, но вне окна/фазы, либо нейтрал/trusted — баннер защищён.
            if (player != null) {
                player.sendActionBar(MM.deserialize(
                        "<red>Флаг можно атаковать только во время боевого окна и только воюющим сторонам.</red>"));
            }
            return;
        }

        flagManager.handleBreak(player, block);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreakBelow(BlockBreakEvent event) {
        Block above = event.getBlock().getRelative(BlockFace.UP);
        if (flagManager.getFlagAt(above) != null) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(MM.deserialize("<red>Нельзя ломать блок под флагом.</red>"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(flagManager::isFlagBlock);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(flagManager::isFlagBlock);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (flagManager.isFlagBlock(b)) { event.setCancelled(true); return; }
            if (flagManager.isFlagBlock(b.getRelative(BlockFace.UP))) { event.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (flagManager.isFlagBlock(b)) { event.setCancelled(true); return; }
            if (flagManager.isFlagBlock(b.getRelative(BlockFace.UP))) { event.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (flagManager.isFlagBlock(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        flagManager.spawnDisplaysInChunk(event.getChunk());
    }
}