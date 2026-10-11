package ru.example.townywar.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Публикуется при изменении бонуса стартовых очков города
 * (как через API, так и через /twar admin addbonus|setbonus).
 * <p>
 * Не публикуется, если oldBonus == newBonus (no-op).
 */
public final class TownStartingBonusChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String town;
    private final int oldBonus;
    private final int newBonus;
    private final String actor;

    public TownStartingBonusChangeEvent(@NotNull String town,
                                        int oldBonus,
                                        int newBonus,
                                        @NotNull String actor) {
        this.town = town;
        this.oldBonus = oldBonus;
        this.newBonus = newBonus;
        this.actor = actor;
    }

    public @NotNull String town() { return town; }
    public int oldBonus() { return oldBonus; }
    public int newBonus() { return newBonus; }
    public int delta() { return newBonus - oldBonus; }
    public @NotNull String actor() { return actor; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}