package ru.example.townywar.listener;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.assault.AssaultTntItemFactory;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarManager;

/**
 * Запрещает ванильный TNT (постановка/поджог/выброс диспенсером)
 * в чанках воюющих городов на всех фазах активной войны.
 * Штурмовой ТНТ — единственный разрешённый разрушитель.
 */
public final class VanillaTntBanListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final TownyWarPlugin plugin;
    private final WarManager warManager;

    public VanillaTntBanListener(TownyWarPlugin plugin, WarManager warManager) {
        this.plugin = plugin;
        this.warManager = warManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || item.getType() != Material.TNT) return;
        if (AssaultTntItemFactory.isTnt(plugin, item)) return;

        if (!isWarringChunk(event.getBlockPlaced())) return;

        event.setCancelled(true);
        event.getPlayer().sendActionBar(MM.deserialize(
                "<red>Ванильный ТНТ запрещён в землях воюющих городов. Используйте Штурмовой ТНТ.</red>"));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        if (block == null || block.getType() != Material.TNT) return;

        // Штурмовой ТНТ — это Material.TNT с записью в AssaultTntManager.
        if (plugin.getAssaultTntManager() != null
                && plugin.getAssaultTntManager().isTntBlock(block)) return;

        if (!isWarringChunk(block)) return;

        event.setCancelled(true);
        Player p = event.getPlayer();
        if (p != null) {
            p.sendActionBar(MM.deserialize(
                    "<red>Ванильный ТНТ запрещён в землях воюющих городов.</red>"));
        }
    }

    /** Ловим выброс TNT из диспенсера в воюющих чанках. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDispense(BlockDispenseEvent event) {
        if (event.getItem().getType() != Material.TNT) return;
        Block dispenser = event.getBlock();
        if (dispenser == null) return;

        Block target = dispenser.getRelative(getDispenseFace(dispenser));
        if (!isWarringChunk(target)) return;

        event.setCancelled(true);
    }

    private org.bukkit.block.BlockFace getDispenseFace(Block dispenser) {
        var data = dispenser.getBlockData();
        if (data instanceof org.bukkit.block.data.Directional dir) return dir.getFacing();
        return org.bukkit.block.BlockFace.UP;
    }

    private boolean isWarringChunk(Block block) {
        if (block == null || block.getWorld() == null) return false;
        var towny = plugin.getTownyHook().api();
        if (!towny.isTownyWorld(block.getWorld())) return false;
        TownBlock tb = towny.getTownBlock(block.getLocation());
        if (tb == null || !tb.hasTown()) return false;
        Town chunkTown = tb.getTownOrNull();
        if (chunkTown == null) return false;
        War war = warManager.getWar(chunkTown.getName());
        return war != null && war.isActive();
    }
}