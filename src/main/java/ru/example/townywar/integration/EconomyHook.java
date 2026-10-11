package ru.example.townywar.integration;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class EconomyHook {

    private final boolean available;
    @Nullable
    private final Economy economy;

    public EconomyHook(JavaPlugin plugin) {
        Economy eco = null;
        try {
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) eco = rsp.getProvider();
        } catch (Throwable t) {
            plugin.getLogger().warning("Vault economy недоступна: " + t.getMessage());
        }
        this.economy = eco;
        this.available = eco != null;
    }

    public boolean isAvailable() { return available; }

    @Nullable
    public Economy economy() { return economy; }

    public double getBalance(OfflinePlayer p) {
        return economy == null ? 0.0 : economy.getBalance(p);
    }
}