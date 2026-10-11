package ru.example.townywar.war;

import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import ru.example.townywar.api.WarSnapshot;
import ru.example.townywar.api.event.TownStartingBonusChangeEvent;
import ru.example.townywar.api.event.WarDeclaredEvent;
import ru.example.townywar.api.event.WarEndedEvent;
import ru.example.townywar.api.event.WarPhaseChangeEvent;
import ru.example.townywar.assault.AssaultTntManager;
import ru.example.townywar.config.ConfigManager;
import ru.example.townywar.config.MessagesManager;
import ru.example.townywar.database.TownWarSettingsRepository;
import ru.example.townywar.database.WarLogRepository;
import ru.example.townywar.database.WarRepository;
import ru.example.townywar.flag.FlagManager;
import ru.example.townywar.negotiation.NegotiationManager;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class WarManager {

    /** Верхняя граница лимита флагов захвата — синхронно с admin-командой. */
    private static final int MAX_CAPTURE_FLAGS_UPPER = 64;

    private final JavaPlugin plugin;
    private final WarRepository repo;
    private final WarLogRepository log;
    private final TownWarSettingsRepository townSettings;
    private final ConfigManager config;
    private final MessagesManager messages;

    private final Map<Long, War> activeWars = new ConcurrentHashMap<>();
    private final Map<String, Long> townToWar = new ConcurrentHashMap<>();
    private final Map<String, Integer> maxFlagsOverride = new ConcurrentHashMap<>();
    private final Map<String, Integer> startingBonusOverride = new ConcurrentHashMap<>();
    private BukkitTask ticker;

    /** Очередь событий, отложенных до конца tick-итерации. */
    private final List<Runnable> pendingEvents = new ArrayList<>();
    private boolean inTick = false;

    /** Устанавливается из TownyWarPlugin.onEnable() после создания NegotiationManager. */
    private volatile NegotiationManager negotiationManager;

    /** Устанавливается из TownyWarPlugin.onEnable() после создания FlagManager. */
    private volatile FlagManager flagManager;

    /** Устанавливается из TownyWarPlugin.onEnable() после создания AssaultTntManager. */
    private volatile AssaultTntManager assaultTntManager;

    public WarManager(JavaPlugin plugin, WarRepository repo, WarLogRepository log,
                      TownWarSettingsRepository townSettings,
                      ConfigManager config, MessagesManager messages) {
        this.plugin = plugin; this.repo = repo; this.log = log;
        this.townSettings = townSettings;
        this.config = config; this.messages = messages;
    }

    public void setNegotiationManager(NegotiationManager negotiationManager) {
        this.negotiationManager = negotiationManager;
    }

    public void setFlagManager(FlagManager flagManager) {
        this.flagManager = flagManager;
    }

    public void setAssaultTntManager(AssaultTntManager m) {
        this.assaultTntManager = m;
    }

    public void loadActiveWars() {
        activeWars.clear();
        townToWar.clear();
        for (War w : repo.loadActive()) {
            activeWars.put(w.getId(), w);
            townToWar.put(w.getAttackerTown().toLowerCase(), w.getId());
            townToWar.put(w.getDefenderTown().toLowerCase(), w.getId());
        }
    }

    public void loadTownSettings() {
        maxFlagsOverride.clear();
        startingBonusOverride.clear();
        for (var e : townSettings.loadAll().entrySet()) {
            String key = e.getKey();
            var s = e.getValue();
            if (s.maxFlags() != config.getCaptureDefaultMaxPerTown()) {
                maxFlagsOverride.put(key, s.maxFlags());
            }
            if (s.startingBonus() != 0) {
                startingBonusOverride.put(key, s.startingBonus());
            }
        }
    }

    public void startTicker() {
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /**
     * A3: shutdown.
     * Останавливаем тикер, затем flushAndShutdown() в репозитории —
     * он дожидается pending async-апдейтов, синхронно пишет финальное
     * состояние каждой активной войны и закрывает executor'ы.
     */
    public void shutdown() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        repo.flushAndShutdown(new ArrayList<>(activeWars.values()));
    }

    private void tick() {
        inTick = true;
        try {
            long now = System.currentTimeMillis();

            List<War> negotiationTimeouts = null;

            for (War w : activeWars.values()) {
                switch (w.getPhase()) {
                    case PREPARATION -> {
                        if (now >= w.getPrepEndsAt()) {
                            String oldPhase = w.getPhase().name();
                            w.setPhase(WarPhase.COMBAT);
                            w.setCombatStartedAt(now);
                            repo.update(w);
                            log.log(w.getId(), null, "PHASE_COMBAT", null);
                            fireEvent(new WarPhaseChangeEvent(w.snapshot(), oldPhase, "COMBAT"));
                            Bukkit.getServer().sendMessage(messages.format("phase-combat-start",
                                    "attacker", w.getAttackerTown(), "defender", w.getDefenderTown()));
                        }
                    }
                    case COMBAT -> {
                        if (w.getAttackerPoints() <= 0 || w.getDefenderPoints() <= 0) {
                            // D3: явный trigger, чтобы по логу было видно причину
                            // перехода COMBAT → NEGOTIATION без корреляции с
                            // предыдущими событиями.
                            log.log(w.getId(), null, "NEGOTIATION_TRIGGER", "cause=points-zero");
                            startNegotiation(w);
                        }
                    }
                    case NEGOTIATION -> {
                        if (w.getNegotiationEndsAt() != null && now >= w.getNegotiationEndsAt()) {
                            if (negotiationTimeouts == null) negotiationTimeouts = new ArrayList<>(2);
                            negotiationTimeouts.add(w);
                        }
                    }
                    default -> { }
                }
            }

            if (negotiationTimeouts != null) {
                for (War w : negotiationTimeouts) handleNegotiationTimeout(w);
            }
        } finally {
            inTick = false;
            flushPendingEvents();
        }
    }

    /**
     * Обработка истечения окна переговоров.
     * <p>
     * A9: если NegotiationManager уже применяет исход этой войны (например,
     * идёт батч-аннексия чанков), мы НЕ дёргаем applyOutcome повторно и НЕ
     * форсим endWar — иначе прервём перенос на середине. Ждём, пока фоновый
     * процесс сам завершит войну через endWar(...).
     * <p>
     * В остальных случаях применяем TRIBUTE-fallback и завершаем войну.
     */
    private void handleNegotiationTimeout(War w) {
        NegotiationManager nm = this.negotiationManager;
        if (nm != null) {
            if (nm.isProcessing(w.getId())) return;

            try {
                nm.applyOutcome(w, WarOutcome.TRIBUTE, null);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE,
                        "Не удалось применить TRIBUTE по таймауту для войны #" + w.getId(), t);
            }
        } else {
            plugin.getLogger().warning("NegotiationManager не подключён — таймаут войны #"
                    + w.getId() + " завершён без применения исхода.");
        }

        if (activeWars.containsKey(w.getId())) {
            endWar(w, w.getWinnerTown(), "timeout-fallback");
        }
    }

    public boolean isAtWar(String name) { return name != null && townToWar.containsKey(name.toLowerCase()); }

    @Nullable public War getWar(String name) {
        if (name == null) return null;
        Long id = townToWar.get(name.toLowerCase());
        return id == null ? null : activeWars.get(id);
    }

    @Nullable public War getWarById(long id) { return activeWars.get(id); }

    public Collection<War> getActiveWars() { return Collections.unmodifiableCollection(activeWars.values()); }

    public boolean isCombatWindowActive() {
        ZonedDateTime now = ZonedDateTime.now(config.getZoneId());
        int m = now.getHour() * 60 + now.getMinute();
        return m >= config.getWindowStartMinutes() && m < config.getWindowEndMinutes();
    }

    public int getMaxConcurrentFlags(String name) {
        if (name == null) return config.getCaptureDefaultMaxPerTown();
        return maxFlagsOverride.getOrDefault(name.toLowerCase(), config.getCaptureDefaultMaxPerTown());
    }

    /**
     * Итоговые стартовые очки для города при объявлении войны.
     * base (config default) + bonus, клампится в [min-total, max-total].
     * Если бонус не задан — возвращается база.
     */
    public int getStartingPoints(String townName) {
        int base = config.getDefaultPoints();
        int bonus = townName == null ? 0
                : startingBonusOverride.getOrDefault(townName.toLowerCase(), 0);
        return clampTotal(base + bonus);
    }

    public int getStartingBonus(String townName) {
        if (townName == null) return 0;
        return startingBonusOverride.getOrDefault(townName.toLowerCase(), 0);
    }

    private int clampTotal(int v) {
        return Math.max(config.getMinTotalPoints(),
                Math.min(config.getMaxTotalPoints(), v));
    }

    @Nullable public War declare(String attacker, String defender) {
        long now = System.currentTimeMillis();
        long prepEnds = now + config.getPreparationHours() * 3600_000L;
        War w = new War(0, attacker, defender,
                getStartingPoints(attacker),
                getStartingPoints(defender),
                WarPhase.PREPARATION, now, prepEnds, null, null, null, null, null);
        try { w = repo.insert(w); }
        catch (Exception e) { plugin.getLogger().severe("war insert: " + e.getMessage()); return null; }
        activeWars.put(w.getId(), w);
        townToWar.put(attacker.toLowerCase(), w.getId());
        townToWar.put(defender.toLowerCase(), w.getId());
        log.log(w.getId(), null, "WAR_DECLARED",
                "attacker=" + attacker + ";defender=" + defender);

        WarSnapshot snap = w.snapshot();
        fireEvent(new WarDeclaredEvent(snap));
        fireEvent(new WarPhaseChangeEvent(snap, WarPhaseChangeEvent.NONE, "PREPARATION"));

        return w;
    }

    public void cancel(War w, String reason) { endWar(w, null, reason); }

    public void endWar(War w, String winner, String reason) {
        endWar(w, winner, reason, true);
    }

    /**
     * A1/A8: при завершении войны (любым путём) снимаем все её флаги —
     * визуал, индексы в FlagManager, строки в placed_flags.
     * Assault TNT: та же логика — снимаем все TNT этой войны (визуал,
     * индексы, строки в assault_tnt).
     * A3: освобождаем executor войны после финального repo.update(w).
     */
    public void endWar(War w, String winner, String reason, boolean announce) {
        boolean wasActive = w.getPhase() != WarPhase.ENDED;
        if (wasActive) {
            String oldPhase = w.getPhase().name();
            w.setPhase(WarPhase.ENDED);
            w.setEndedAt(System.currentTimeMillis());
            w.setWinnerTown(winner);
            w.setOutcome(reason);
            repo.update(w);
            log.log(w.getId(), null, "WAR_ENDED", "winner=" + winner + ";reason=" + reason);

            WarSnapshot snap = w.snapshot();
            fireEvent(new WarPhaseChangeEvent(snap, oldPhase, "ENDED"));
            fireEvent(new WarEndedEvent(snap, winner, reason));
        }
        activeWars.remove(w.getId());
        townToWar.remove(w.getAttackerTown().toLowerCase(), w.getId());
        townToWar.remove(w.getDefenderTown().toLowerCase(), w.getId());

        FlagManager fm = this.flagManager;
        if (fm != null) fm.removeAllByWar(w.getId());

        AssaultTntManager atm = this.assaultTntManager;
        if (atm != null) atm.removeAllByWar(w.getId());

        repo.releaseExecutor(w.getId());

        if (announce && wasActive) {
            Bukkit.getServer().sendMessage(messages.format("war-ended",
                    "attacker", w.getAttackerTown(), "defender", w.getDefenderTown()));
        }
    }

    /**
     * A2: перегрузка без явного победителя. Используется тикером, когда
     * очки одной из сторон дошли до 0 — там победитель выводится из
     * состояния очков.
     */
    public void startNegotiation(War w) {
        if (w.getPhase() == WarPhase.NEGOTIATION || w.getPhase() == WarPhase.ENDED) return;
        if (w.getAttackerPoints() <= 0 && w.getDefenderPoints() <= 0) {
            log.log(w.getId(), null, "WAR_DRAW", "0-0");
            cancel(w, "draw");
            return;
        }
        String winner = w.getAttackerPoints() <= 0 ? w.getDefenderTown() : w.getAttackerTown();
        startNegotiation(w, winner);
    }

    /**
     * A2: перегрузка с явным победителем. Используется флагом капитуляции —
     * победитель определяется по стороне, установившей флаг, а не по очкам.
     * <p>
     * Флаги здесь не трогаем: их удалит FlagManager.tick() на следующем тике
     * (фаза перестанет быть COMBAT) либо endWar, если война завершится
     * немедленно.
     */
    public void startNegotiation(War w, String winner) {
        if (w.getPhase() == WarPhase.NEGOTIATION || w.getPhase() == WarPhase.ENDED) return;
        if (winner == null) {
            plugin.getLogger().warning("startNegotiation: winner == null для войны #" + w.getId());
            return;
        }
        String oldPhase = w.getPhase().name();
        w.setPhase(WarPhase.NEGOTIATION);
        w.setNegotiationEndsAt(System.currentTimeMillis() + config.getNegotiationMinutes() * 60_000L);
        w.setWinnerTown(winner);
        repo.update(w);
        log.log(w.getId(), null, "PHASE_NEGOTIATION", "winner=" + winner);
        fireEvent(new WarPhaseChangeEvent(w.snapshot(), oldPhase, "NEGOTIATION"));
        Bukkit.getServer().sendMessage(messages.format("negotiation-start",
                "minutes", String.valueOf(config.getNegotiationMinutes())));
    }

    public void changePoints(War w, WarSide side, int delta) {
        if (w.getPhase() == WarPhase.ENDED) return;
        if (side == WarSide.ATTACKER) w.setAttackerPoints(Math.max(0, w.getAttackerPoints() + delta));
        else w.setDefenderPoints(Math.max(0, w.getDefenderPoints() + delta));
        repo.update(w);
    }

    public void addPointsAdmin(String townName, int amount, String actor) {
        War w = getWar(townName);
        if (w == null) return;
        WarSide side = w.sideOf(townName);
        if (side == null) return;
        changePoints(w, side, amount);
        log.log(w.getId(), actor, "ADMIN_ADD_POINTS", "town=" + townName + ";amount=" + amount);
        if (w.getAttackerPoints() <= 0 || w.getDefenderPoints() <= 0) startNegotiation(w);
    }

    public void removePointsAdmin(String town, int amount, String actor) { addPointsAdmin(town, -amount, actor); }

    public void setPointsAdmin(String townName, int points, String actor) {
        War w = getWar(townName);
        if (w == null) return;
        WarSide side = w.sideOf(townName);
        if (side == null) return;
        int clamped = Math.max(0, points);
        if (side == WarSide.ATTACKER) w.setAttackerPoints(clamped);
        else w.setDefenderPoints(clamped);
        repo.update(w);
        log.log(w.getId(), actor, "ADMIN_SET_POINTS", "town=" + townName + ";points=" + clamped);
        if (w.getAttackerPoints() <= 0 || w.getDefenderPoints() <= 0) startNegotiation(w);
    }

    /**
     * Устанавливает лимит одновременных Флагов Захвата для города.
     * Клампит в [1, 64]. Возвращает false, если город не найден в Towny
     * или имя пустое — в этом случае ничего не меняется.
     */
    public synchronized boolean setMaxConcurrentFlags(String townName, int count, String actor) {
        if (townName == null || townName.isBlank()) return false;
        if (!townExists(townName)) {
            plugin.getLogger().warning("[WarManager] setMaxConcurrentFlags: город '"
                    + townName + "' не найден в Towny — лимит не установлен.");
            return false;
        }

        int clamped = Math.max(1, Math.min(MAX_CAPTURE_FLAGS_UPPER, count));
        String key = townName.toLowerCase();

        if (clamped == config.getCaptureDefaultMaxPerTown()) {
            maxFlagsOverride.remove(key);
        } else {
            maxFlagsOverride.put(key, clamped);
        }
        persistTownSettings(key);
        log.log(null, actor, "ADMIN_SET_MAX_FLAGS",
                "town=" + townName + ";count=" + clamped
                        + (clamped != count ? ";requested=" + count : ""));
        return true;
    }

    /**
     * Атомарно прибавляет delta к бонусу стартовых очков города.
     * В отличие от {@link #setStartingBonus(String, int, String)} — не
     * перезаписывает, а суммирует с текущим бонусом.
     * <p>
     * Возвращает false, если город не найден в Towny или итог
     * (default + newBonus) выходит за [min-total, max-total] — тогда
     * ничего не меняется.
     */
    public synchronized boolean addStartingBonus(String townName, int delta, String actor) {
        if (townName == null || townName.isBlank()) return false;
        if (!townExists(townName)) {
            plugin.getLogger().warning("[WarManager] addStartingBonus: город '"
                    + townName + "' не найден в Towny — бонус не начислен.");
            return false;
        }

        String key = townName.toLowerCase();
        int oldBonus = startingBonusOverride.getOrDefault(key, 0);
        int newBonus = oldBonus + delta;
        int total = config.getDefaultPoints() + newBonus;
        if (total < config.getMinTotalPoints() || total > config.getMaxTotalPoints()) {
            return false;
        }
        if (newBonus == 0) startingBonusOverride.remove(key);
        else startingBonusOverride.put(key, newBonus);
        persistTownSettings(key);
        log.log(null, actor, "ADMIN_ADD_STARTING_BONUS",
                "town=" + townName + ";delta=" + delta
                        + ";bonus=" + newBonus + ";total=" + total);

        if (oldBonus != newBonus) {
            fireEvent(new TownStartingBonusChangeEvent(townName, oldBonus, newBonus, actor));
        }
        return true;
    }

    /**
     * Устанавливает абсолютное значение бонуса. Используется админ-командой
     * /twar admin setbonus и внешними вызовами, где нужен именно «set».
     * <p>
     * ВАЖНО: параметр newBonus — это целевое значение бонуса, а не дельта.
     * Для сложения использовать {@link #addStartingBonus(String, int, String)}.
     */
    public synchronized boolean setStartingBonus(String townName, int newBonus, String actor) {
        if (townName == null || townName.isBlank()) return false;
        if (!townExists(townName)) {
            plugin.getLogger().warning("[WarManager] setStartingBonus: город '"
                    + townName + "' не найден в Towny — бонус не установлен.");
            return false;
        }

        int total = config.getDefaultPoints() + newBonus;
        if (total < config.getMinTotalPoints() || total > config.getMaxTotalPoints()) return false;

        String key = townName.toLowerCase();
        int oldBonus = startingBonusOverride.getOrDefault(key, 0);

        if (newBonus == 0) startingBonusOverride.remove(key);
        else startingBonusOverride.put(key, newBonus);
        persistTownSettings(key);
        log.log(null, actor, "ADMIN_SET_STARTING_BONUS",
                "town=" + townName + ";bonus=" + newBonus + ";total=" + total);

        if (oldBonus != newBonus) {
            fireEvent(new TownStartingBonusChangeEvent(townName, oldBonus, newBonus, actor));
        }
        return true;
    }

    /**
     * Синхронизация кэша с БД. Если оба поля вернулись к дефолтам —
     * удаляем строку, иначе upsert с актуальными значениями.
     */
    private void persistTownSettings(String key) {
        int maxFlags = maxFlagsOverride.getOrDefault(key, config.getCaptureDefaultMaxPerTown());
        int bonus = startingBonusOverride.getOrDefault(key, 0);
        if (maxFlags == config.getCaptureDefaultMaxPerTown() && bonus == 0) {
            townSettings.delete(key);
        } else {
            townSettings.upsert(key, maxFlags, bonus);
        }
    }

    public void forceEnd(String town, String reason, String actor) {
        War w = getWar(town);
        if (w == null) return;
        log.log(w.getId(), actor, "ADMIN_FORCE_END", "town=" + town + ";reason=" + reason);
        endWar(w, null, reason);
    }

    /**
     * B5: defense in depth. Даже если командный слой забудет провалидировать,
     * здесь не создаём войну с одинаковыми / пустыми именами и не допускаем
     * повторного объявления при активной войне.
     */
    @Nullable public War adminDeclare(String attacker, String defender, String actor) {
        if (attacker == null || defender == null) return null;
        if (attacker.isBlank() || defender.isBlank()) return null;
        if (attacker.equalsIgnoreCase(defender)) return null;
        if (isAtWar(attacker) || isAtWar(defender)) return null;
        War w = declare(attacker, defender);
        if (w != null) log.log(w.getId(), actor, "ADMIN_DECLARE", null);
        return w;
    }

    /**
     * B4: при переводе в NEGOTIATION обязательно должен быть winnerTown —
     * иначе NegotiationCommand не пустит мэра в меню, а таймаут применит
     * TRIBUTE с winner == null и зациклит endWar.
     * <p>
     * При переводе в COMBAT очищаем negotiation-поля, чтобы повторный
     * переход в NEGOTIATION не дал мгновенный таймаут (negotiationEndsAt
     * уже в прошлом).
     * <p>
     * При переводе в PREPARATION откатываем всё, что относится к поздним
     * фазам.
     */
    public boolean setPhase(String town, WarPhase phase, String actor) {
        War w = getWar(town);
        if (w == null || w.getPhase() == WarPhase.ENDED) return false;

        String oldPhase = w.getPhase().name();
        long now = System.currentTimeMillis();
        switch (phase) {
            case PREPARATION -> {
                w.setCombatStartedAt(null);
                w.setNegotiationEndsAt(null);
                w.setWinnerTown(null);
                w.setOutcome(null);
            }
            case COMBAT -> {
                if (w.getCombatStartedAt() == null) w.setCombatStartedAt(now);
                w.setNegotiationEndsAt(null);
                w.setWinnerTown(null);
                w.setOutcome(null);
            }
            case NEGOTIATION -> {
                if (w.getWinnerTown() == null) {
                    String winner = resolveWinnerByPoints(w);
                    if (winner == null) {
                        return false;
                    }
                    w.setWinnerTown(winner);
                }
                w.setNegotiationEndsAt(now + config.getNegotiationMinutes() * 60_000L);
            }
            default -> { }
        }

        w.setPhase(phase);
        repo.update(w);
        log.log(w.getId(), actor, "ADMIN_SET_PHASE", "phase=" + phase.name());
        fireEvent(new WarPhaseChangeEvent(w.snapshot(), oldPhase, phase.name()));
        return true;
    }

    private String resolveWinnerByPoints(War w) {
        if (w.getAttackerPoints() > w.getDefenderPoints()) return w.getAttackerTown();
        if (w.getDefenderPoints() > w.getAttackerPoints()) return w.getDefenderTown();
        return null;
    }

    /**
     * Проверяет, что город зарегистрирован в Towny.
     * При падении/перезагрузке Towny возвращает true, чтобы не блокировать
     * административные вызовы — БД-инварианты это не ломает, а ложное
     * отклонение было бы хуже.
     */
    private boolean townExists(String name) {
        try {
            return TownyAPI.getInstance().getTown(name) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    public List<War> snapshotWars() { return List.copyOf(activeWars.values()); }

    // ===== Event dispatch =====

    /**
     * Публикует Bukkit-событие.
     * <ul>
     *   <li>Если вызов не в main-thread — уходит в scheduler main-таска.</li>
     *   <li>Если мы внутри {@link #tick()} — откладывается до конца итерации,
     *       чтобы слушатель не мутировал {@code activeWars} во время обхода.</li>
     *   <li>Иначе — публикуется сразу.</li>
     * </ul>
     */
    private void fireEvent(Event event) {
        Runnable dispatch = () -> {
            try {
                Bukkit.getPluginManager().callEvent(event);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE,
                        "Event dispatch failed: " + event.getClass().getSimpleName(), t);
            }
        };

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, dispatch);
            return;
        }
        if (inTick) {
            pendingEvents.add(dispatch);
            return;
        }
        dispatch.run();
    }

    private void flushPendingEvents() {
        if (pendingEvents.isEmpty()) return;
        List<Runnable> toRun = new ArrayList<>(pendingEvents);
        pendingEvents.clear();
        for (Runnable r : toRun) {
            try { r.run(); }
            catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Deferred event dispatch failed", t);
            }
        }
    }
}