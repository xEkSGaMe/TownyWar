package ru.example.townywar.negotiation;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.economy.Account;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.config.ConfigManager;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public final class TributeScheduler {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final TownyWarPlugin plugin;
    private final TributeRepository repo;
    private final ConfigManager config;

    /**
     * C9: кэш в памяти вместо синхронного loadAllActive() на main-thread
     * каждые 30 секунд. Обновляется async-задачей раз в минуту.
     */
    private volatile List<TributeRepository.TributeEntry> cache = List.of();

    private BukkitTask ticker;
    private BukkitTask refreshTask;

    public TributeScheduler(TownyWarPlugin plugin, TributeRepository repo, ConfigManager config) {
        this.plugin = plugin; this.repo = repo; this.config = config;
    }

    public void start() {
        // Initial sync load — один раз при включении. Параллельных операций
        // в этот момент нет, main-thread блокируется на один SELECT.
        this.cache = repo.loadAllActive();

        // C10: главный тик раз в 5 секунд, обрабатывает не более одного
        // трибута за тик через processQueue.
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);

        // C9: async-refresh раз в минуту.
        this.refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::refreshCache, 1200L, 1200L);
    }

    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        if (refreshTask != null) { refreshTask.cancel(); refreshTask = null; }
    }

    private void refreshCache() {
        if (!plugin.isEnabled()) return;
        try { cache = repo.loadAllActive(); }
        catch (Throwable t) { plugin.getLogger().warning("Tribute cache refresh failed: " + t.getMessage()); }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        List<TributeRepository.TributeEntry> due = new ArrayList<>();
        for (TributeRepository.TributeEntry e : cache) {
            if (now >= e.nextAt()) due.add(e);
        }
        if (due.isEmpty()) return;
        processQueue(due);
    }

    /**
     * C10: по одному трибуту за тик. Если после длительного простоя
     * просрочено 30 трибутов — растянется на 30 тиков (~1.5 сек), а не
     * зафрит main-thread одним залпом из 30 withdraw+deposit+UPDATE.
     */
    private void processQueue(List<TributeRepository.TributeEntry> due) {
        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                if (i >= due.size()) { cancel(); return; }
                try { process(due.get(i)); }
                catch (Throwable t) { plugin.getLogger().warning("tribute process failed: " + t.getMessage()); }
                i++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void process(TributeRepository.TributeEntry e) {
        Town payer = plugin.getTownyHook().getTown(e.payerTown());
        Town receiver = plugin.getTownyHook().getTown(e.receiverTown());
        if (payer == null || receiver == null) { repo.deactivateSync(e.id()); return; }
        Account pa = payer.getAccount(), ra = receiver.getAccount();
        if (pa == null || ra == null) return;

        double bal = pa.getHoldingBalance();
        if (bal <= 0) {
            plugin.getWarLogRepository().log(e.warId(), null, "TRIBUTE_SKIPPED",
                    "payer=" + payer.getName() + ";reason=empty");
            scheduleNext(e); return;
        }
        double amount = bal * e.percent() / 100.0;
        if (amount <= 0) { scheduleNext(e); return; }
        if (!pa.withdraw(amount, "War tribute")) { scheduleNext(e); return; }

        // Rollback: если deposit не прошёл — возвращаем списанное плательщику.
        if (!ra.deposit(amount, "War tribute")) {
            plugin.getLogger().warning("TRIBUTE deposit failed, откат: " + amount
                    + " от " + payer.getName() + " к " + receiver.getName()
                    + " (tribute #" + e.id() + ")");
            if (!pa.deposit(amount, "War tribute rollback")) {
                plugin.getLogger().severe("TRIBUTE rollback failed! Потеряно " + amount
                        + " между " + payer.getName() + " и " + receiver.getName()
                        + " (tribute #" + e.id() + ", война #" + e.warId() + ")");
            }
            scheduleNext(e);
            return;
        }

        plugin.getWarLogRepository().log(e.warId(), null, "TRIBUTE_PAID",
                "amount=" + amount + ";from=" + payer.getName() + ";to=" + receiver.getName());
        broadcastToTown(payer, "<red>Выплачена контрибуция <yellow>-" + String.format("%.0f", amount)
                + "</yellow> городу <yellow>" + MM.escapeTags(receiver.getName()) + "</yellow>.</red>");
        broadcastToTown(receiver, "<gold>Получена контрибуция <yellow>+" + String.format("%.0f", amount)
                + "</yellow> от <yellow>" + MM.escapeTags(payer.getName()) + "</yellow>.</gold>");
        scheduleNext(e);
    }

    private void scheduleNext(TributeRepository.TributeEntry e) {
        ZonedDateTime next = ZonedDateTime.now(config.getZoneId())
                .withHour(config.getTributeHour()).withMinute(config.getTributeMinute())
                .withSecond(0).withNano(0).plusDays(1);
        repo.updateNextAtSync(e.id(), next.toInstant().toEpochMilli());
    }

    private void broadcastToTown(Town town, String message) {
        var msg = MM.deserialize(message);
        for (Resident r : town.getResidents()) { Player p = r.getPlayer(); if (p != null) p.sendMessage(msg); }
    }
}