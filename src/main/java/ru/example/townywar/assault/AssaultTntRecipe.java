package ru.example.townywar.assault;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import ru.example.townywar.TownyWarPlugin;

public final class AssaultTntRecipe {

    private AssaultTntRecipe() { }

    public static void register(TownyWarPlugin plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "assault_tnt");
        Bukkit.removeRecipe(key);

        ShapedRecipe recipe = new ShapedRecipe(key, AssaultTntItemFactory.create(plugin));
        recipe.shape("STS", "TRT", "STS");
        recipe.setIngredient('S', Material.STRING);
        recipe.setIngredient('T', Material.TNT);
        recipe.setIngredient('R', Material.REDSTONE_BLOCK);
        Bukkit.addRecipe(recipe);

        plugin.getLogger().info("Рецепт Штурмового ТНТ зарегистрирован.");
    }
}