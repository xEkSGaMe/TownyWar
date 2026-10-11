package ru.example.townywar;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import ru.example.townywar.api.TownyWarAPI;
import ru.example.townywar.api.TownyWarAPIImpl;
import ru.example.townywar.assault.AssaultTntManager;
import ru.example.townywar.assault.AssaultTntRecipe;
import ru.example.townywar.assault.AssaultTntRepository;
import ru.example.townywar.command.TownyCommandInterceptor;
import ru.example.townywar.command.WarListCommand;
import ru.example.townywar.config.ConfigManager;
import ru.example.townywar.config.MessagesManager;
import ru.example.townywar.database.Database;
import ru.example.townywar.database.TownWarSettingsRepository;
import ru.example.townywar.database.WarLogRepository;
import ru.example.townywar.database.WarRepository;
import ru.example.townywar.flag.FlagManager;
import ru.example.townywar.flag.FlagRecipe;
import ru.example.townywar.flag.FlagRepository;
import ru.example.townywar.glow.GlowManager;
import ru.example.townywar.integration.EconomyHook;
import ru.example.townywar.integration.TownyHook;
import ru.example.townywar.integration.TownyWarPlaceholderExpansion;
import ru.example.townywar.listener.ArmorStandListener;
import ru.example.townywar.listener.AssaultTntListener;
import ru.example.townywar.listener.CombatContributionListener;
import ru.example.townywar.listener.CommandLockListener;
import ru.example.townywar.listener.FlagListener;
import ru.example.townywar.listener.HangingListener;
import ru.example.townywar.listener.PlayerJoinListener;
import ru.example.townywar.listener.PvPListener;
import ru.example.townywar.listener.PvpDeathListener;
import ru.example.townywar.listener.VanillaTntBanListener;
import ru.example.townywar.listener.TownyActionListener;
import ru.example.townywar.listener.WarshopDamageListener;
import ru.example.townywar.menu.WarListMenu;
import ru.example.townywar.menu.WarStatsMenu;
import ru.example.townywar.negotiation.NegotiationManager;
import ru.example.townywar.negotiation.NegotiationMenu;
import ru.example.townywar.negotiation.TributeRepository;
import ru.example.townywar.negotiation.TributeScheduler;
import ru.example.townywar.stats.WarStatsRepository;
import ru.example.townywar.war.BossbarManager;
import ru.example.townywar.war.CombatWindowManager;
import ru.example.townywar.war.WarManager;
import ru.example.townywar.warshop.WarshopCommand;
import ru.example.townywar.warshop.WarshopManager;
import ru.example.townywar.warshop.WarshopMenu;
import ru.example.townywar.warshop.WarshopRepository;

public final class TownyWarPlugin extends JavaPlugin {

    private static TownyWarPlugin instance;

    private ConfigManager configManager;
    private MessagesManager messagesManager;
    private Database database;
    private WarRepository warRepository;
    private WarLogRepository warLogRepository;
    private FlagRepository flagRepository;
    private TributeRepository tributeRepository;
    private TownWarSettingsRepository townSettingsRepository;
    private TownyHook townyHook;
    private EconomyHook economyHook;
    private WarManager warManager;
    private CombatWindowManager combatWindowManager;
    private BossbarManager bossbarManager;
    private FlagManager flagManager;
    private NegotiationManager negotiationManager;
    private NegotiationMenu negotiationMenu;
    private TributeScheduler tributeScheduler;
    private WarListMenu warListMenu;
    private GlowManager glowManager;
    private WarshopRepository warshopRepository;
    private WarshopManager warshopManager;
    private WarshopMenu warshopMenu;
    private PvpDeathListener pvpDeathListener;

    // Assault TNT
    private AssaultTntRepository assaultTntRepository;
    private AssaultTntManager assaultTntManager;

    // War Stats
    private WarStatsRepository warStatsRepository;
    private WarStatsMenu warStatsMenu;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().kickOnPacketException(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        PacketEvents.getAPI().init();

        this.configManager = new ConfigManager(this);
        this.messagesManager = new MessagesManager(this);

        this.townyHook = new TownyHook(this);
        if (!townyHook.isAvailable()) {
            getLogger().severe("Towny не найден — выключаемся.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.economyHook = new EconomyHook(this);
        if (!economyHook.isAvailable()) {
            getLogger().warning("Vault economy недоступна — денежные операции работать не будут.");
        }

        this.database = new Database(this, configManager);
        if (!database.connect()) {
            getLogger().severe("Не удалось подключиться к БД — выключаемся.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.warRepository = new WarRepository(database, this);
        this.warLogRepository = new WarLogRepository(database, this);
        this.flagRepository = new FlagRepository(database, this);
        this.tributeRepository = new TributeRepository(database, this);
        this.townSettingsRepository = new TownWarSettingsRepository(database);

        this.warManager = new WarManager(this, warRepository, warLogRepository,
                townSettingsRepository, configManager, messagesManager);
        this.warManager.loadActiveWars();
        this.warManager.loadTownSettings();
        this.warManager.startTicker();

        this.combatWindowManager = new CombatWindowManager(this, configManager, warManager);
        this.combatWindowManager.start();

        this.bossbarManager = new BossbarManager(this, warManager, configManager);
        this.bossbarManager.start();

        // Warshop должен быть поднят ДО flagManager.start():
        // FlagManager.loadFromDatabase() читает isTruceActive(warId) у WarshopManager.
        this.warshopRepository = new WarshopRepository(database, this);
        this.warshopManager = new WarshopManager(this, warshopRepository);
        this.warshopManager.load();
        this.warshopMenu = new WarshopMenu(this, warshopManager);

        // ===== Публичный API =====
        getServer().getServicesManager().register(
                TownyWarAPI.class,
                new TownyWarAPIImpl(warManager, warshopManager),
                this,
                ServicePriority.Normal
        );
        getLogger().info("TownyWar API зарегистрирован (v" + TownyWarAPI.API_VERSION + ").");

        Bukkit.getScheduler().runTaskTimer(this, warshopManager::cleanupExpired, 20L, 20L);

        // Теперь безопасно: truce уже в памяти.
        this.flagManager = new FlagManager(this, configManager, warManager, combatWindowManager, flagRepository);
        this.flagManager.start();

        // Assault TNT up — ПОСЛЕ flagManager, т.к. explode() зовёт flagManager.isFlagBlock().
        this.assaultTntRepository = new AssaultTntRepository(database, this);
        this.assaultTntManager = new AssaultTntManager(this, configManager, warManager,
                combatWindowManager, assaultTntRepository);
        this.assaultTntManager.start();
        this.warManager.setAssaultTntManager(this.assaultTntManager);

        this.negotiationManager = new NegotiationManager(this, warManager, tributeRepository);
        this.negotiationMenu = new NegotiationMenu(this, negotiationManager);
        this.warListMenu = new WarListMenu(this);

        // War Stats
        this.warStatsRepository = new WarStatsRepository(database);
        this.warStatsMenu = new WarStatsMenu(this, warStatsRepository);

        this.warManager.setNegotiationManager(this.negotiationManager);
        this.warManager.setFlagManager(this.flagManager);

        this.tributeScheduler = new TributeScheduler(this, tributeRepository, configManager);
        this.tributeScheduler.start();

        this.glowManager = new GlowManager(this, townyHook, warManager);
        this.glowManager.start();

        FlagRecipe.registerAll(this);
        AssaultTntRecipe.register(this);

        Bukkit.getPluginManager().registerEvents(
                new TownyCommandInterceptor(this, warManager, negotiationMenu,
                        new WarListCommand(this, warListMenu),
                        new WarshopCommand(this, warshopMenu)),
                this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new TownyActionListener(this, warManager, combatWindowManager), this);
        Bukkit.getPluginManager().registerEvents(new HangingListener(this, combatWindowManager), this);
        Bukkit.getPluginManager().registerEvents(new ArmorStandListener(this, combatWindowManager), this);
        Bukkit.getPluginManager().registerEvents(new CommandLockListener(this, warManager), this);
        Bukkit.getPluginManager().registerEvents(new PvPListener(this, warManager, combatWindowManager), this);
        this.pvpDeathListener = new PvpDeathListener(this, warManager, combatWindowManager);
        Bukkit.getPluginManager().registerEvents(this.pvpDeathListener, this);
        Bukkit.getPluginManager().registerEvents(new FlagListener(this, flagManager), this);
        Bukkit.getPluginManager().registerEvents(new WarshopDamageListener(this, warshopManager), this);
        Bukkit.getPluginManager().registerEvents(new VanillaTntBanListener(this, warManager), this);
        Bukkit.getPluginManager().registerEvents(new AssaultTntListener(this, assaultTntManager), this);
        Bukkit.getPluginManager().registerEvents(warStatsMenu, this);
        Bukkit.getPluginManager().registerEvents(
                new CombatContributionListener(this, warManager, combatWindowManager), this);
        Bukkit.getPluginManager().registerEvents(warshopMenu, this);
        Bukkit.getPluginManager().registerEvents(negotiationMenu, this);
        Bukkit.getPluginManager().registerEvents(warListMenu, this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new TownyWarPlaceholderExpansion(this).register();
                getLogger().info("PlaceholderAPI-плейсхолдеры зарегистрированы.");
            } catch (Throwable t) {
                getLogger().warning("Не удалось зарегистрировать PAPI-плейсхолдеры: " + t.getMessage());
            }
        }

        getLogger().info("TownyWar запущен. Активных войн: " + warManager.getActiveWars().size()
                + ", флагов: " + flagManager.allFlags().size()
                + ", PacketEvents: " + (glowManager.isAvailable() ? "да" : "нет"));
        getLogger().info("Боевое окно: " + (combatWindowManager.isCombatWindowActive() ? "открыто" : "закрыто"));
    }

    @Override
    public void onDisable() {
        if (glowManager != null) glowManager.stop();
        if (warshopManager != null) warshopManager.shutdown();
        if (tributeScheduler != null) tributeScheduler.stop();
        if (pvpDeathListener != null) pvpDeathListener.stop();
        if (negotiationMenu != null) negotiationMenu.closeAll();
        // Assault TNT — ДО flagManager.stop(), чтобы при снятии TNT-дисплеев
        // flagManager ещё был жив (консистентно).
        if (assaultTntManager != null) assaultTntManager.stop();
        if (flagManager != null) flagManager.stop();
        if (bossbarManager != null) bossbarManager.stop();
        if (combatWindowManager != null) combatWindowManager.stop();
        if (warManager != null) warManager.shutdown();
        if (database != null) database.close();
        PacketEvents.getAPI().terminate();
    }

    public static TownyWarPlugin get() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public MessagesManager getMessages() { return messagesManager; }
    public WarManager getWarManager() { return warManager; }
    public CombatWindowManager getCombatWindowManager() { return combatWindowManager; }
    public BossbarManager getBossbarManager() { return bossbarManager; }
    public FlagManager getFlagManager() { return flagManager; }
    public NegotiationManager getNegotiationManager() { return negotiationManager; }
    public GlowManager getGlowManager() { return glowManager; }
    public TownyHook getTownyHook() { return townyHook; }
    public EconomyHook getEconomyHook() { return economyHook; }
    public WarRepository getWarRepository() { return warRepository; }
    public WarLogRepository getWarLogRepository() { return warLogRepository; }
    public WarshopManager getWarshopManager() { return warshopManager; }
    public AssaultTntManager getAssaultTntManager() { return assaultTntManager; }
    public WarStatsMenu getWarStatsMenu() { return warStatsMenu; }
    public WarListMenu getWarListMenu() { return warListMenu; }

    /** Удобный доступ к API из других плагинов. Может вернуть null. */
    public static @Nullable TownyWarAPI getAPI() {
        var rsp = Bukkit.getServicesManager().getRegistration(TownyWarAPI.class);
        return rsp == null ? null : rsp.getProvider();
    }
}