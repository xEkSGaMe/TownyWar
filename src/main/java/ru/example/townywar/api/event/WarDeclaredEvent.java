package ru.example.townywar.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import ru.example.townywar.api.WarSnapshot;

/**
 * Публикуется один раз — в момент успешного объявления войны.
 * Фаза на момент вызова — PREPARATION.
 */
public final class WarDeclaredEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final WarSnapshot war;

    public WarDeclaredEvent(@NotNull WarSnapshot war) {
        this.war = war;
    }

    public @NotNull WarSnapshot war() { return war; }
    public @NotNull String attackerTown() { return war.attackerTown(); }
    public @NotNull String defenderTown() { return war.defenderTown(); }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}