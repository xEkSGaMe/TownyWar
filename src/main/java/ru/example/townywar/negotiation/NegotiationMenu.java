package ru.example.townywar.negotiation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarOutcome;
import ru.example.townywar.war.WarPhase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NegotiationMenu implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE_PLAIN = "Переговоры";
    private static final int SLOT_ANNEXATION = 11;
    private static final int SLOT_LOOT = 13;
    private static final int SLOT_TRIBUTE = 15;

    /** D2: информационная легенда. */
    private static final int SLOT_LEGEND = 22;

    private final TownyWarPlugin plugin;
    private final NegotiationManager manager;

    private final Map<UUID, Long> open = new HashMap<>();
    private final Map<UUID, Boolean> choosing = new HashMap<>();

    public NegotiationMenu(TownyWarPlugin plugin, NegotiationManager manager) {
        this.plugin = plugin; this.manager = manager;
    }

    public static final class Holder implements InventoryHolder {
        private final long warId;
        private Inventory inv;
        public Holder(long warId) { this.warId = warId; }
        public long getWarId() { return warId; }
        @Override public Inventory getInventory() { return inv; }
        void setInventory(Inventory inv) { this.inv = inv; }
    }

    public void open(Player player, War war) {
        Holder holder = new Holder(war.getId());
        Inventory inv = Bukkit.createInventory(holder, 27,
                MM.deserialize("<dark_red><bold>" + TITLE_PLAIN + "</bold></dark_red>"));
        holder.setInventory(inv);

        ItemStack bg = glass(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        inv.setItem(SLOT_ANNEXATION, buildItem(Material.ANVIL, "<dark_red><bold>Аннексия</bold></dark_red>",
                List.of("<gray>Город противника удаляется,</gray>",
                        "<gray>все его чанки переходят вам.</gray>",
                        "", "<yellow>Нажмите, чтобы выбрать.</yellow>")));

        boolean canLoot = manager.canApply(war, WarOutcome.LOOT);
        if (canLoot) {
            inv.setItem(SLOT_LOOT, buildItem(Material.CHEST, "<gold><bold>Разграбить</bold></gold>",
                    List.of("<gray>Вся казна города противника</gray>",
                            "<gray>переходит вам.</gray>", "", "<yellow>Нажмите, чтобы выбрать.</yellow>")));
        } else {
            inv.setItem(SLOT_LOOT, buildItem(Material.BARRIER, "<dark_gray><bold>Разграбить (недоступно)</bold></dark_gray>",
                    List.of("<red>Казна города противника пуста.</red>", "<gray>Выберите другой вариант.</gray>")));
        }

        inv.setItem(SLOT_TRIBUTE, buildItem(Material.GOLD_INGOT, "<yellow><bold>Контрибуция</bold></yellow>",
                List.of("<gray>Город противника ежедневно</gray>",
                        "<gray>отчисляет вам <white>" + plugin.getConfigManager().getTributePercent() + "%</white>.</gray>",
                        "", "<yellow>Нажмите, чтобы выбрать.</yellow>")));

        // D2: легенда с описанием трёх исходов и напоминанием про команду.
        inv.setItem(SLOT_LEGEND, buildItem(Material.BOOK, "<yellow><bold>Как выбрать</bold></yellow>",
                List.of("<gray>Один из трёх исходов войны:</gray>",
                        "",
                        "<dark_red>● Аннексия</dark_red> <gray>— город противника</gray>",
                        "<gray>  удаляется, чанки переходят вам.</gray>",
                        "<gold>● Разграбить</gold> <gray>— вся казна</gray>",
                        "<gray>  противника переходит вам.</gray>",
                        "<yellow>● Контрибуция</yellow> <gray>— ежедневный</gray>",
                        "<gray>  процент от казны противника.</gray>",
                        "",
                        "<gray>Закрыть меню — вернуться позже:</gray>",
                        "<yellow>/t negotiation</yellow>")));

        open.put(player.getUniqueId(), war.getId());
        player.openInventory(inv);
    }

    private ItemStack glass(Material mat) {
        ItemStack s = new ItemStack(mat); ItemMeta m = s.getItemMeta();
        if (m != null) { m.displayName(Component.text(" ")); s.setItemMeta(m); }
        return s;
    }

    private ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack s = new ItemStack(mat); ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
            m.lore(lore.stream().map(l -> MM.deserialize(l).decoration(TextDecoration.ITALIC, false)).toList());
            s.setItemMeta(m);
        }
        return s;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;

        Long registered = open.get(player.getUniqueId());
        if (registered == null || registered.longValue() != holder.getWarId()) return;

        event.setCancelled(true);

        War war = plugin.getWarManager().getWarById(holder.getWarId());
        if (war == null || war.getPhase() != WarPhase.NEGOTIATION) { closeAndForget(player); return; }

        int slot = event.getRawSlot();
        WarOutcome outcome = switch (slot) {
            case SLOT_ANNEXATION -> WarOutcome.ANNEXATION;
            case SLOT_LOOT -> WarOutcome.LOOT;
            case SLOT_TRIBUTE -> WarOutcome.TRIBUTE;
            default -> null;
        };
        if (outcome == null) return;
        if (!manager.canApply(war, outcome)) {
            player.sendActionBar(MM.deserialize("<red>Этот исход сейчас недоступен.</red>"));
            return;
        }
        choosing.put(player.getUniqueId(), true);
        manager.applyOutcome(war, outcome, player);
        closeAndForget(player);
        player.closeInventory();
    }

    /**
     * C7: убрано авто-переоткрытие. Раньше при закрытии меню (Esc, клик
     * мимо) оно переоткрывалось, что при частых закрытиях давало цикл.
     * Теперь: закрытие = освобождение мапы. Игрок может вернуться через
     * /t negotiation; боссбар напомнит.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (open.containsKey(player.getUniqueId())) closeAndForget(player);
    }

    public void closeAndForget(Player player) {
        open.remove(player.getUniqueId()); choosing.remove(player.getUniqueId());
    }

    public void closeAll() {
        for (UUID id : List.copyOf(open.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) { closeAndForget(p); p.closeInventory(); }
            else { open.remove(id); choosing.remove(id); }
        }
    }
}