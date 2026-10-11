package ru.example.townywar.assault;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.example.townywar.TownyWarPlugin;

import java.util.List;

public final class AssaultTntItemFactory {

    private static NamespacedKey KEY;

    private AssaultTntItemFactory() { }

    public static NamespacedKey key(TownyWarPlugin plugin) {
        if (KEY == null) KEY = new NamespacedKey(plugin, "war_assault_tnt");
        return KEY;
    }

    public static ItemStack create(TownyWarPlugin plugin) {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text("Штурмовой ТНТ")
                .color(NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        int fuse = plugin.getConfigManager().getAssaultTntFuseSeconds();
        int dur = plugin.getConfigManager().getAssaultTntDurability();

        meta.lore(List.of(
                Component.text("Активируется через " + fuse + " сек после установки.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Игнорирует защиту Towny.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Прочность: " + dur + ".", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Ставить только в чанках противника.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, "1");
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isTnt(TownyWarPlugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (item.getType() != Material.TNT) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(key(plugin), PersistentDataType.STRING);
    }
}