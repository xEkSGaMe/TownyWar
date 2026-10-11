package ru.example.townywar.flag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.townywar.TownyWarPlugin;

import java.util.List;
import java.util.UUID;

/**
 * Предмет-флаг. Хранит в PDC: тип флага, владельца (город), war_id,
 * уникальный id флага, максимальную прочность.
 */
public final class FlagItem {

    public static final NamespacedKey KEY_TYPE;
    public static final NamespacedKey KEY_OWNER;
    public static final NamespacedKey KEY_WAR;
    public static final NamespacedKey KEY_FLAG_ID;
    public static final NamespacedKey KEY_DURABILITY;
    public static final NamespacedKey KEY_MAX_DURABILITY;

    static {
        JavaPlugin plugin = TownyWarPlugin.get();
        KEY_TYPE           = new NamespacedKey(plugin, "flag_type");
        KEY_OWNER          = new NamespacedKey(plugin, "flag_owner");
        KEY_WAR            = new NamespacedKey(plugin, "flag_war");
        KEY_FLAG_ID        = new NamespacedKey(plugin, "flag_id");
        KEY_DURABILITY     = new NamespacedKey(plugin, "flag_durability");
        KEY_MAX_DURABILITY = new NamespacedKey(plugin, "flag_max_durability");
    }

    private FlagItem() { }

    /** Создаёт новый предмет-флаг без привязки к конкретной войне. */
    public static ItemStack create(FlagType type) {
        Material mat = type == FlagType.CAPTURE ? Material.RED_BANNER : Material.GREEN_BANNER;
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();

        Component name = type == FlagType.CAPTURE
                ? MiniMessage.miniMessage().deserialize("<bold><dark_red>Флаг Захвата</dark_red></bold>")
                : MiniMessage.miniMessage().deserialize("<bold><dark_green>Флаг Капитуляции</dark_green></bold>");

        meta.displayName(name.decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                MiniMessage.miniMessage().deserialize("<gray>Установите в чанке противника,</gray>")
                        .decoration(TextDecoration.ITALIC, false),
                MiniMessage.miniMessage().deserialize("<gray>чтобы начать захват.</gray>")
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                MiniMessage.miniMessage().deserialize("<dark_gray>Тип: <white>"
                                + (type == FlagType.CAPTURE ? "Захват" : "Капитуляция") + "</white></dark_gray>")
                        .decoration(TextDecoration.ITALIC, false)
        ));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_TYPE, PersistentDataType.STRING, type.name());
        // Флаг не привязан к городу/войне — это определяется в момент установки.

        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isFlag(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(KEY_TYPE, PersistentDataType.STRING);
    }

    public static FlagType getType(ItemStack stack) {
        if (!isFlag(stack)) return null;
        String raw = stack.getItemMeta().getPersistentDataContainer()
                .get(KEY_TYPE, PersistentDataType.STRING);
        try { return FlagType.valueOf(raw); } catch (Exception e) { return null; }
    }

    public static UUID getFlagId(ItemStack stack) {
        if (!isFlag(stack)) return null;
        String raw = stack.getItemMeta().getPersistentDataContainer()
                .get(KEY_FLAG_ID, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return UUID.fromString(raw); } catch (Exception e) { return null; }
    }
}