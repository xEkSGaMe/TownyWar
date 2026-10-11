package ru.example.townywar.menu;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.util.TimeUtil;
import ru.example.townywar.war.War;

import java.util.ArrayList;
import java.util.List;

public final class WarListMenu implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE_RAW = "<dark_red><bold>Войны</bold></dark_red>";
    private static final int SIZE = 45;
    private static final String SEPARATOR = "<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬</dark_gray>";

    private final TownyWarPlugin plugin;

    public WarListMenu(TownyWarPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * C3: holder хранит ссылку на инвентарь, getInventory() больше не
     * возвращает null. Сторонние плагины, читающие holder.getInventory()
     * из события, не получат NPE.
     * <p>
     * WarStats: параллельный список warIds по слотам — позволяет по
     * {@code event.getRawSlot()} восстановить, по какой именно войне
     * кликнул игрок, без повторного обхода snapshotWars() (и без риска,
     * что состав войн успел измениться между open() и click).
     */
    public static final class Holder implements InventoryHolder {
        private final List<Long> warIds = new ArrayList<>();
        private Inventory inv;
        public List<Long> getWarIds() { return warIds; }
        @Override public Inventory getInventory() { return inv; }
        void setInventory(Inventory inv) { this.inv = inv; }
    }

    public void open(Player player) {
        var wars = plugin.getWarManager().snapshotWars();
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, MM.deserialize(TITLE_RAW));
        holder.setInventory(inv);
        int i = 0;
        for (War w : wars) {
            if (i >= SIZE) break;
            inv.setItem(i, buildIcon(w));
            holder.getWarIds().add(w.getId());
            i++;
        }
        player.openInventory(inv);
    }

    private int countOnline(String townName) {
        Town town = plugin.getTownyHook().getTown(townName);
        if (town == null) return 0;
        int count = 0;
        for (Resident r : town.getResidents()) {
            Player p = r.getPlayer();
            if (p != null && p.isOnline()) count++;
        }
        return count;
    }

    /** C1: имена городов из Towny экранируем — иначе город с '<' в имени
     *  ломает MiniMessage-разметку лора. */
    private ItemStack buildIcon(War w) {
        long now = System.currentTimeMillis();
        Material mat;
        boolean glint = false;
        List<String> lore = new ArrayList<>();

        String attackerEsc = MM.escapeTags(w.getAttackerTown());
        String defenderEsc = MM.escapeTags(w.getDefenderTown());

        switch (w.getPhase()) {
            case PREPARATION -> {
                mat = Material.DIAMOND_SWORD;
                lore.add(SEPARATOR);
                lore.add("<gray>Время до начала войны: <white>"
                        + TimeUtil.format(Math.max(0, w.getPrepEndsAt() - now)) + "</white></gray>");
                lore.add("<red>Оправдание войны</red>");
            }
            case COMBAT -> {
                mat = Material.NETHERITE_SWORD;
                lore.add(SEPARATOR);
                lore.add("<red>Активные боевые действия</red>");
                lore.add("");
                int aOnline = countOnline(w.getAttackerTown());
                int dOnline = countOnline(w.getDefenderTown());
                lore.add("<white>Онлайн сторон: </white>"
                        + "<red>" + attackerEsc + "</red> "
                        + "<gold>" + aOnline + "</gold>"
                        + "<gray> / </gray>"
                        + "<red>" + defenderEsc + "</red> "
                        + "<gold>" + dOnline + "</gold>");
            }
            case NEGOTIATION -> {
                mat = Material.NETHERITE_SWORD;
                glint = true;
                lore.add(SEPARATOR);
                String winner = w.getWinnerTown() == null
                        ? "<gray>не определён</gray>"
                        : "<white>" + MM.escapeTags(w.getWinnerTown()) + "</white>";
                lore.add("<gray>Победитель: " + winner + "</gray>");
                lore.add("<red>Переговоры</red>");
            }
            default -> {
                mat = Material.BARRIER;
                lore.add("<gray>Война завершена</gray>");
            }
        }

        // WarStats: подсказка, что по ПКМ открывается статистика.
        lore.add("");
        lore.add("<dark_gray>ПКМ — статистика вклада</dark_gray>");

        String displayName = switch (w.getPhase()) {
            case PREPARATION -> "<white>" + attackerEsc
                    + " <gray>vs</gray> " + defenderEsc + "</white>";
            case COMBAT, NEGOTIATION -> "<white>" + attackerEsc
                    + " <white>(<red>" + w.getAttackerPoints() + "</red>)</white>"
                    + " <gray>vs</gray> "
                    + "<white>" + defenderEsc
                    + " <white>(<red>" + w.getDefenderPoints() + "</red>)</white></white>";
            default -> "<gray>" + attackerEsc + " vs " + defenderEsc + "</gray>";
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(MM.deserialize(displayName).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
                .map(l -> MM.deserialize(l).decoration(TextDecoration.ITALIC, false))
                .toList());
        if (glint) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);

        // WarStats: ПКМ по иконке войны → статистика вклада за эту войну.
        if (!event.isRightClick()) return;
        int slot = event.getRawSlot();
        List<Long> ids = holder.getWarIds();
        if (slot < 0 || slot >= ids.size()) return;
        plugin.getWarStatsMenu().open(player, ids.get(slot));
    }
}