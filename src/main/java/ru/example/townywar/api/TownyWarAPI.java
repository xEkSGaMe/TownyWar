package ru.example.townywar.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Публичный API TownyWar для интеграции с другими плагинами.
 *
 * <p>Получение экземпляра:
 * <pre>{@code
 * var rsp = Bukkit.getServicesManager().getRegistration(TownyWarAPI.class);
 * if (rsp == null) return;
 * TownyWarAPI api = rsp.getProvider();
 * }</pre>
 *
 * <p>Все методы должны вызываться из главного потока сервера.
 */
public interface TownyWarAPI {

    /** Текущая версия контракта. Растёт при ломающих изменениях. */
    int API_VERSION = 1;

    int getApiVersion();

    // ===== Стартовые очки (до войны) =====

    /** Текущий бонус без базового значения. 0 — если не задан. */
    int getStartingBonus(@NotNull String town);

    /** Итог: base + bonus, заклампленный в [min-total, max-total]. */
    int getStartingPoints(@NotNull String town);

    /**
     * Атомарно прибавляет delta к бонусу. Основной метод для
     * интеграций типа «квест выполнен → +50 очков».
     * <p>false — если итог вышел за границы конфига, ничего не меняется.
     */
    boolean addStartingBonus(@NotNull String town, int delta, @NotNull String actor);

    /** Абсолютная установка бонуса. Для админ-сценариев. */
    boolean setStartingBonus(@NotNull String town, int value, @NotNull String actor);

    // ===== Лимиты флагов =====

    int getMaxConcurrentFlags(@NotNull String town);

    boolean setMaxConcurrentFlags(@NotNull String town, int count, @NotNull String actor);

    // ===== Состояние войны =====

    boolean isAtWar(@NotNull String town);

    boolean isTruceActive(@NotNull String town);

    /** Снимок войны города или null. */
    @Nullable WarSnapshot getWar(@NotNull String town);

    // ===== Очки во время войны =====

    boolean addPoints(@NotNull String town, int amount, @NotNull String actor);

    boolean removePoints(@NotNull String town, int amount, @NotNull String actor);

    boolean setPoints(@NotNull String town, int points, @NotNull String actor);
}