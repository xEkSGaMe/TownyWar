package ru.example.townywar.flag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ru.example.townywar.TownyWarPlugin;

/**
 * Фабрика предметов-флагов.
 *
 * <p>Основной PDC-ключ — {@code war_flag} (значение — имя {@link FlagType}).
 * Дополнительно читаем legacy-ключи {@code flag_type} и {@code raid_flag},
 * чтобы старые предметы, скрафченные в предыдущих версиях, продолжали
 * работать и корректно распознавались.
 */
public final class FlagItemFactory {

    private static NamespacedKey KEY;
    private static NamespacedKey LEGACY_KEY;

    private FlagItemFactory() { }

    public static NamespacedKey key(TownyWarPlugin plugin) {
        if (KEY == null) KEY = new NamespacedKey(plugin, "war_flag");
        return KEY;
    }

    public static NamespacedKey legacyKey(TownyWarPlugin plugin) {
        if (LEGACY_KEY == null) LEGACY_KEY = new NamespacedKey(plugin, "flag_type");
        return LEGACY_KEY;
    }

    public static ItemStack create(TownyWarPlugin plugin, FlagType type) {
        Material mat = type == FlagType.CAPTURE ? Material.RED_BANNER : Material.GREEN_BANNER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Component name = type == FlagType.CAPTURE
                ? Component.text("Флаг Захвата").color(NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
                : Component.text("Флаг Капитуляции").color(NamedTextColor.DARK_GREEN)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);

        meta.displayName(name);

        if (type == FlagType.CAPTURE) {
            meta.lore(java.util.List.of(
                    Component.text("Установите в чанке противника,", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("смежном с вашей территорией.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Продержится 120 секунд — чанк перейдёт вам.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
        } else {
            meta.lore(java.util.List.of(
                    Component.text("Установите в центральном чанке врага.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Продержится 30 минут — война закончится.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
        }

        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isFlag(TownyWarPlugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(key(plugin), PersistentDataType.STRING)
                || pdc.has(legacyKey(plugin), PersistentDataType.STRING);
    }

    public static FlagType getType(TownyWarPlugin plugin, ItemStack item) {
        if (!isFlag(plugin, item)) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        String raw = pdc.get(key(plugin), PersistentDataType.STRING);
        if (raw == null) raw = pdc.get(legacyKey(plugin), PersistentDataType.STRING);
        if (raw == null) return null;

        try { return FlagType.valueOf(raw); }
        catch (IllegalArgumentException e) { return null; }
    }
}