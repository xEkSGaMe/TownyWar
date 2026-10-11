package ru.example.townywar.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.example.townywar.TownyWarPlugin;

public final class TownyWarPlaceholderExpansion extends PlaceholderExpansion {

    private final TownyWarPlugin plugin;

    public TownyWarPlaceholderExpansion(TownyWarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "townywar"; }
    @Override public @NotNull String getAuthor() { return "EksGame"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String p = params.toLowerCase();

        if (p.equals("default_points")) {
            return String.valueOf(plugin.getConfigManager().getDefaultPoints());
        }

        if (p.startsWith("starting_points_")) {
            String town = params.substring("starting_points_".length());
            return String.valueOf(plugin.getWarManager().getStartingPoints(town));
        }
        if (p.startsWith("starting_bonus_")) {
            String town = params.substring("starting_bonus_".length());
            return String.valueOf(plugin.getWarManager().getStartingBonus(town));
        }

        if (p.equals("player_starting_points") || p.equals("player_starting_bonus")) {
            if (player == null) return "0";
            Resident resident = TownyAPI.getInstance().getResident(player.getUniqueId());
            if (resident == null || !resident.hasTown()) return "0";
            Town town = resident.getTownOrNull();
            if (town == null) return "0";
            if (p.equals("player_starting_points")) {
                return String.valueOf(plugin.getWarManager().getStartingPoints(town.getName()));
            }
            return String.valueOf(plugin.getWarManager().getStartingBonus(town.getName()));
        }

        return null;
    }
}