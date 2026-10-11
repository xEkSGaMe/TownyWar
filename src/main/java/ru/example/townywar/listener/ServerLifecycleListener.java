package ru.example.townywar.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.war.CombatWindowManager;

/**
 * На отключении плагина снимает принудительные тоглы PvP/explosion,
 * чтобы сервер не остался в «залипшем» состоянии.
 */
public final class ServerLifecycleListener implements Listener {

    private final CombatWindowManager windowManager;

    public ServerLifecycleListener(TownyWarPlugin plugin, CombatWindowManager windowManager) {
        this.windowManager = windowManager;
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (!event.getPlugin().getName().equals("TownyWar")) return;
        windowManager.stop();
    }
}