package ru.example.townywar.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.example.townywar.api.WarSnapshot;

/**
 * Публикуется при завершении войны, дополнительно к
 * {@link WarPhaseChangeEvent} с newPhase = "ENDED".
 * <p>
 * {@code reason} — машинно-читаемая причина завершения:
 * "ANNEXATION", "LOOT", "TRIBUTE", "cancelled-by-attacker",
 * "admin-force-end", "draw", "timeout-fallback" и т.д.
 * Совпадает со значением {@code outcome} в {@link WarSnapshot}.
 * <p>
 * {@code winnerTown} может быть null для ничейных и админских отмен.
 */
public final class WarEndedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final WarSnapshot war;
    private final @Nullable String winnerTown;
    private final @NotNull String reason;

    public WarEndedEvent(@NotNull WarSnapshot war,
                         @Nullable String winnerTown,
                         @NotNull String reason) {
        this.war = war;
        this.winnerTown = winnerTown;
        this.reason = reason;
    }

    public @NotNull WarSnapshot war() { return war; }
    public @Nullable String winnerTown() { return winnerTown; }
    public @NotNull String reason() { return reason; }

    public boolean hasWinner() { return winnerTown != null; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}