package ru.example.townywar.warshop;

import org.bukkit.Material;

public enum WarshopItem {
    INSPIRATION("inspiration", "Воодушевление", Material.BLAZE_POWDER),
    TRUCE("truce", "Перемирие", Material.WHITE_BANNER),
    MERCENARIES("mercenaries", "Призыв наёмников", Material.IRON_SWORD);

    private final String key;
    private final String displayName;
    private final Material icon;

    WarshopItem(String key, String displayName, Material icon) {
        this.key = key;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String key() { return key; }
    public String displayName() { return displayName; }
    public Material icon() { return icon; }
}