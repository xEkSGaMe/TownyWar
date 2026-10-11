package ru.example.townywar;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import ru.example.townywar.command.TownyWarCommandRegistrar;

@SuppressWarnings("UnstableApiUsage")
public final class TownyWarBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        LifecycleEventManager<BootstrapContext> lm = context.getLifecycleManager();
        lm.registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new TownyWarCommandRegistrar().register(event.registrar()));
    }

    @Override
    public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
        return new TownyWarPlugin();
    }
}