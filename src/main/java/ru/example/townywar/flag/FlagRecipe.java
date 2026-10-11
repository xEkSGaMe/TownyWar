package ru.example.townywar.flag;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import ru.example.townywar.TownyWarPlugin;

/**
 * Регистрирует рецепты обоих флагов.
 *
 * <p>Перед добавлением удаляет старые рецепты с теми же ключами —
 * {@link Bukkit#addRecipe} не перезаписывает существующие, из-за чего
 * после обновления плагина оставался старый рецепт, выдающий устаревший
 * предмет без актуальных PDC-меток.
 */
public final class FlagRecipe {

    private FlagRecipe() { }

    public static void registerAll(TownyWarPlugin plugin) {
        NamespacedKey captureKey = new NamespacedKey(plugin, "capture_flag");
        NamespacedKey surrenderKey = new NamespacedKey(plugin, "surrender_flag");

        // Сносим старые рецепты с этими ключами (если были).
        Bukkit.removeRecipe(captureKey);
        Bukkit.removeRecipe(surrenderKey);

        // --- Флаг Захвата ---
        ShapedRecipe capture = new ShapedRecipe(captureKey, FlagItemFactory.create(plugin, FlagType.CAPTURE));
        capture.shape("RW ", "RW ", " S ");
        capture.setIngredient('R', Material.REDSTONE_BLOCK);
        capture.setIngredient('W', Material.RED_WOOL);
        capture.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(capture);

        // --- Флаг Капитуляции ---
        ShapedRecipe surrender = new ShapedRecipe(surrenderKey, FlagItemFactory.create(plugin, FlagType.SURRENDER));
        surrender.shape("DG ", "DG ", " S ");
        surrender.setIngredient('D', Material.DIAMOND_BLOCK);
        surrender.setIngredient('G', Material.GREEN_WOOL);
        surrender.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(surrender);

        plugin.getLogger().info("Рецепты флагов зарегистрированы.");
    }
}