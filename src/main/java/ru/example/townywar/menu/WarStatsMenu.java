package ru.example.townywar.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.stats.WarPlayerStats;
import ru.example.townywar.stats.WarStatsRepository;
import ru.example.townywar.war.War;

import java.util.List;

public final class WarStatsMenu implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int SIZE = 54;
    private static final int MAX_ENTRIES = 45;
    private static final int SLOT_EMPTY = 22;
    private static final int SLOT_BACK = 49;
    private static final String SEPARATOR =
            "<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray>";

    private final TownyWarPlugin plugin;
    private final WarStatsRepository repo;

    public WarStatsMenu(TownyWarPlugin plugin, WarStatsRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
    }

    public static final class Holder implements InventoryHolder {
        private final long warId;
        private Inventory inv;
        public Holder(long warId) { this.warId = warId; }
        public long getWarId() { return warId; }
        @Override public Inventory getInventory() { return inv; }
        void setInventory(Inventory inv) { this.inv = inv; }
    }

    public void open(Player player, long warId) {
        War war = plugin.getWarManager().getWarById(warId);
        if (war == null) return;

        List<WarPlayerStats> stats = repo.loadForWar(warId);

        String title = "<dark_red><bold>Вклад: "
                + MM.escapeTags(war.getAttackerTown())
                + " vs " + MM.escapeTags(war.getDefenderTown())
                + "</bold></dark_red>";

        Holder holder = new Holder(warId);
        Inventory inv = Bukkit.createInventory(holder, SIZE, MM.deserialize(title));
        holder.setInventory(inv);

        for (int i = 0; i < SIZE; i++) inv.setItem(i, glass());

        int slot = 0;
        for (WarPlayerStats s : stats) {
            if (slot >= MAX_ENTRIES) break;
            inv.setItem(slot++, buildHead(s));
        }

        if (slot == 0) inv.setItem(SLOT_EMPTY, buildEmpty());

        inv.setItem(SLOT_BACK, buildBack());
        player.openInventory(inv);
    }

    private ItemStack buildHead(WarPlayerStats s) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        OfflinePlayer op = Bukkit.getOfflinePlayer(s.playerName());
        try { meta.setOwningPlayer(op); } catch (Throwable ignored) { }

        meta.displayName(MM.deserialize("<white><bold>" + MM.escapeTags(s.playerName()) + "</bold></white>")
                .decoration(TextDecoration.ITALIC, false));

        List<String> lore = List.of(
                SEPARATOR,
                "<white>Убийств: <red>" + s.kills() + "</red></white>",
                "<white>Смертей: <red>" + s.deaths() + "</red></white>",
                "",
                "<white>Поставлено флагов: <red>" + s.flagsPlaced() + "</red></white>",
                "<white>Сломано флагов: <red>" + s.flagsBroken() + "</red></white>",
                "<white>Съедено золотых яблок: <red>" + s.gapples() + "</red></white>",
                "<white>Использовано эндер-перлов: <red>" + s.pearls() + "</red></white>"
        );
        meta.lore(lore.stream()
                .map(l -> MM.deserialize(l).decoration(TextDecoration.ITALIC, false))
                .toList());
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack glass() {
        ItemStack s = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) { m.displayName(Component.text(" ")); s.setItemMeta(m); }
        return s;
    }

    private ItemStack buildEmpty() {
        ItemStack s = new ItemStack(Material.BARRIER);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(MM.deserialize("<dark_gray><bold>Пока никто не отличился</bold></dark_gray>")
                    .decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(MM.deserialize(
                            "<gray>Участники войны ещё не снесли очки, не ели яблоки и не ставили флаги.</gray>")
                    .decoration(TextDecoration.ITALIC, false)));
            s.setItemMeta(m);
        }
        return s;
    }

    private ItemStack buildBack() {
        ItemStack s = new ItemStack(Material.ARROW);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(MM.deserialize("<yellow><bold>← К списку войн</bold></yellow>")
                    .decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(MM.deserialize("<gray>Вернуться к /t warlist</gray>")
                    .decoration(TextDecoration.ITALIC, false)));
            s.setItemMeta(m);
        }
        return s;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);

        if (event.getRawSlot() == SLOT_BACK) {
            plugin.getWarListMenu().open(player);
        }
    }
}