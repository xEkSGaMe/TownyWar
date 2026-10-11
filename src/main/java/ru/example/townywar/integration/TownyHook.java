package ru.example.townywar.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class TownyHook {

    private final boolean available;

    public TownyHook(JavaPlugin plugin) {
        this.available = Bukkit.getPluginManager().isPluginEnabled("Towny");
    }

    public boolean isAvailable() { return available; }

    public TownyAPI api() { return TownyAPI.getInstance(); }

    @Nullable
    public Town getTown(@Nullable String name) {
        if (name == null) return null;
        return api().getTown(name);
    }

    @Nullable
    public Town getTownOf(Player player) {
        return api().getTown(player);
    }

    public boolean isSameNation(Town a, Town b) {
        try {
            return a.hasNation() && b.hasNation()
                    && a.getNation().getName().equalsIgnoreCase(b.getNation().getName());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isNeutral(Town t) { return t.isNeutral(); }

    public boolean isMayor(Town town, Player player) {
        try {
            Resident mayor = town.getMayor();
            return mayor != null && mayor.getUUID().equals(player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }
}