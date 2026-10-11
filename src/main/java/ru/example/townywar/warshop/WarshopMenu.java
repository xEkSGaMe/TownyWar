package ru.example.townywar.warshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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
import ru.example.townywar.war.WarPhase;

import java.util.ArrayList;
import java.util.List;

public final class WarshopMenu implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITLE = "<dark_red><bold>Военный магазин</bold></dark_red>";
    private static final int SIZE = 27;
    private static final int SLOT_INSPIRATION = 11;
    private static final int SLOT_TRUCE = 13;
    private static final int SLOT_MERCENARIES = 15;

    private final TownyWarPlugin plugin;
    private final WarshopManager manager;

    public WarshopMenu(TownyWarPlugin plugin, WarshopManager manager) {
        this.plugin = plugin; this.manager = manager;
    }

    public static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
        void setInventory(Inventory inv) { this.inv = inv; }
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, MM.deserialize(TITLE));
        holder.setInventory(inv);

        for (int i = 0; i < SIZE; i++) inv.setItem(i, glass());
        inv.setItem(SLOT_INSPIRATION, buildItem(WarshopItem.INSPIRATION, player));
        inv.setItem(SLOT_TRUCE, buildItem(WarshopItem.TRUCE, player));
        inv.setItem(SLOT_MERCENARIES, buildItem(WarshopItem.MERCENARIES, player));

        player.openInventory(inv);
    }

    private ItemStack glass() {
        ItemStack s = new ItemStack(org.bukkit.Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = s.getItemMeta();
        if (m != null) { m.displayName(Component.text(" ")); s.setItemMeta(m); }
        return s;
    }

    private ItemStack buildItem(WarshopItem item, Player player) {
        var cfg = plugin.getConfigManager();
        int cost = manager.costOf(item);

        var town = plugin.getTownyHook().getTownOf(player);
        String townName = town == null ? "" : town.getName();
        long cdMs = townName.isEmpty() ? 0 : manager.cooldownRemaining(item, townName);
        boolean onCd = cdMs > 0;

        ItemStack stack = new ItemStack(item.icon());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        List<String> lore = new ArrayList<>();
        String cdLine = onCd
                ? "<red>Перезарядка: " + TimeUtil.format(cdMs) + "</red>"
                : "<green>Готово к покупке</green>";

        switch (item) {
            case INSPIRATION -> {
                lore.add("<gray>Увеличивает урон горожан вашего города</gray>");
                lore.add("<gray>на <white>" + cfg.getInspirationBonusPercent() + "%</white> на <white>"
                        + cfg.getInspirationDurationMin() + " мин</white>.</gray>");
                lore.add("<gray>Длительность: <white>" + cfg.getInspirationDurationMin() + " мин</white></gray>");
                lore.add("<gray>Кулдаун города: <white>" + cfg.getInspirationCooldownMin() + " мин</white></gray>");
            }
            case TRUCE -> {
                lore.add("<gray>Останавливает войну на <white>"
                        + cfg.getTruceDurationMin() + " мин</white>:</gray>");
                lore.add("<gray>— PvP между сторонами блокируется</gray>");
                lore.add("<gray>— таймеры флагов ставятся на паузу</gray>");
                lore.add("<gray>— очки не меняются</gray>");
                lore.add("<gray>Кулдаун города: <white>" + cfg.getTruceCooldownMin() + " мин</white></gray>");
            }
            case MERCENARIES -> {
                lore.add("<gray>Мгновенно даёт вашему городу</gray>");
                lore.add("<gray><white>+" + cfg.getMercenariesPoints() + "</white> боевых очков.</gray>");
                lore.add("<gray>Кулдаун города: <white>" + cfg.getMercenariesCooldownMin() + " мин</white></gray>");
            }
        }

        lore.add("");
        lore.add("<gold>Стоимость: <yellow>" + cost + "</yellow> монет</gold>");
        lore.add(cdLine);
        lore.add("");
        lore.add(onCd ? "<dark_gray>Недоступно</dark_gray>" : "<yellow>Нажмите, чтобы купить.</yellow>");

        meta.displayName(MM.deserialize("<gold><bold>" + item.displayName() + "</bold></gold>")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(l -> MM.deserialize(l).decoration(TextDecoration.ITALIC, false)).toList());
        if (onCd) meta.setEnchantmentGlintOverride(false);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);

        WarshopItem item = switch (event.getRawSlot()) {
            case SLOT_INSPIRATION -> WarshopItem.INSPIRATION;
            case SLOT_TRUCE -> WarshopItem.TRUCE;
            case SLOT_MERCENARIES -> WarshopItem.MERCENARIES;
            default -> null;
        };
        if (item == null) return;

        var town = plugin.getTownyHook().getTownOf(player);
        if (town == null) { player.closeInventory(); return; }
        War war = plugin.getWarManager().getWar(town.getName());
        if (war == null || war.getPhase() != WarPhase.COMBAT) {
            player.sendActionBar(MM.deserialize("<red>Магазин доступен только в фазе боя.</red>"));
            player.closeInventory();
            return;
        }

        WarshopManager.Outcome out = manager.tryBuy(player, item);
        switch (out.result()) {
            case SUCCESS -> {
                player.sendActionBar(MM.deserialize("<green>Покупка совершена: <yellow>"
                        + item.displayName() + "</yellow>.</green>"));
                plugin.getMessages().send(player, "warshop-success", "item", item.displayName());
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) open(player); }, 2L);
            }
            case ON_COOLDOWN -> player.sendActionBar(MM.deserialize(
                    "<red>Город на перезарядке: <yellow>" + TimeUtil.format(out.cooldownRemainingMs()) + "</yellow>.</red>"));
            case NOT_ENOUGH_MONEY -> player.sendActionBar(MM.deserialize("<red>Недостаточно монет.</red>"));
            case ECONOMY_UNAVAILABLE -> player.sendActionBar(MM.deserialize("<red>Экономика недоступна.</red>"));
            case TRUCE_ALREADY_ACTIVE -> player.sendActionBar(MM.deserialize("<red>Перемирие уже действует.</red>"));
            case NOT_IN_COMBAT -> player.sendActionBar(MM.deserialize("<red>Ваш город не в фазе боя.</red>"));
            case TOWN_REQUIRED -> player.sendActionBar(MM.deserialize("<red>Вы не в городе.</red>"));
            case WITHDRAW_FAILED, INTERNAL_ERROR -> player.sendActionBar(
                    MM.deserialize("<red>Не удалось провести покупку.</red>"));
        }
    }
}