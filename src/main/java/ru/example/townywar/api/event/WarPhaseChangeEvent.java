package ru.example.townywar.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import ru.example.townywar.api.WarSnapshot;

/**
 * Публикуется при любом переходе между фазами, включая переход в ENDED.
 * При первом появлении (объявление войны) {@code oldPhase = "NONE"}.
 * <p>
 * Помимо этого события при переходе в ENDED публикуется {@link WarEndedEvent}
 * с расширенными данными (winner, reason).
 */
public final class WarPhaseChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public static final String NONE = "NONE";

    private final WarSnapshot war;
    private final String oldPhase;
    private final String newPhase;

    public WarPhaseChangeEvent(@NotNull WarSnapshot war,
                               @NotNull String oldPhase,
                               @NotNull String newPhase) {
        this.war = war;
        this.oldPhase = oldPhase;
        this.newPhase = newPhase;
    }

    public @NotNull WarSnapshot war() { return war; }
    public @NotNull String oldPhase() { return oldPhase; }
    public @NotNull String newPhase() { return newPhase; }

    public boolean isFromNone() { return NONE.equals(oldPhase); }
    public boolean isEnteringPreparation() { return "PREPARATION".equals(newPhase); }
    public boolean isEnteringCombat() { return "COMBAT".equals(newPhase); }
    public boolean isEnteringNegotiation() { return "NEGOTIATION".equals(newPhase); }
    public boolean isEnding() { return "ENDED".equals(newPhase); }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}