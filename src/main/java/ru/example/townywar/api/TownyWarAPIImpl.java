package ru.example.townywar.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.example.townywar.war.War;
import ru.example.townywar.war.WarManager;
import ru.example.townywar.warshop.WarshopManager;

public final class TownyWarAPIImpl implements TownyWarAPI {

    private final WarManager warManager;
    private final WarshopManager warshopManager;

    public TownyWarAPIImpl(@NotNull WarManager warManager,
                           @NotNull WarshopManager warshopManager) {
        this.warManager = warManager;
        this.warshopManager = warshopManager;
    }

    @Override public int getApiVersion() { return API_VERSION; }

    @Override public int getStartingBonus(@NotNull String town) {
        return warManager.getStartingBonus(town);
    }

    @Override public int getStartingPoints(@NotNull String town) {
        return warManager.getStartingPoints(town);
    }

    @Override public boolean addStartingBonus(@NotNull String town, int delta, @NotNull String actor) {
        return warManager.addStartingBonus(town, delta, actor);
    }

    @Override public boolean setStartingBonus(@NotNull String town, int value, @NotNull String actor) {
        return warManager.setStartingBonus(town, value, actor);
    }

    @Override public int getMaxConcurrentFlags(@NotNull String town) {
        return warManager.getMaxConcurrentFlags(town);
    }

    @Override
    public boolean setMaxConcurrentFlags(@NotNull String town, int count, @NotNull String actor) {
        return warManager.setMaxConcurrentFlags(town, count, actor);
    }

    @Override public boolean isAtWar(@NotNull String town) {
        return warManager.isAtWar(town);
    }

    @Override public boolean isTruceActive(@NotNull String town) {
        War w = warManager.getWar(town);
        return w != null && warshopManager != null && warshopManager.isTruceActive(w.getId());
    }

    @Override @Nullable public WarSnapshot getWar(@NotNull String town) {
        War w = warManager.getWar(town);
        return w == null ? null : w.snapshot();
    }

    @Override public boolean addPoints(@NotNull String town, int amount, @NotNull String actor) {
        if (warManager.getWar(town) == null) return false;
        warManager.addPointsAdmin(town, amount, actor);
        return true;
    }

    @Override public boolean removePoints(@NotNull String town, int amount, @NotNull String actor) {
        if (warManager.getWar(town) == null) return false;
        warManager.removePointsAdmin(town, amount, actor);
        return true;
    }

    @Override public boolean setPoints(@NotNull String town, int points, @NotNull String actor) {
        if (warManager.getWar(town) == null) return false;
        warManager.setPointsAdmin(town, points, actor);
        return true;
    }

    // ===== Per-town оверрайды очков за убийство =====

    @Override public int getEffectiveDeathMayorPoints(@NotNull String town) {
        return warManager.getEffectiveDeathMayor(town);
    }

    @Override public int getEffectiveDeathResidentPoints(@NotNull String town) {
        return warManager.getEffectiveDeathResident(town);
    }

    @Override public boolean setDeathMayorPoints(@NotNull String town,
                                                 @Nullable Integer value,
                                                 @NotNull String actor) {
        return warManager.setDeathMayorPoints(town, value, actor);
    }

    @Override public boolean setDeathResidentPoints(@NotNull String town,
                                                    @Nullable Integer value,
                                                    @NotNull String actor) {
        return warManager.setDeathResidentPoints(town, value, actor);
    }

    @Override @Nullable public Integer getDeathMayorOverride(@NotNull String town) {
        return warManager.getDeathMayorPoints(town);
    }

    @Override @Nullable public Integer getDeathResidentOverride(@NotNull String town) {
        return warManager.getDeathResidentPoints(town);
    }
}