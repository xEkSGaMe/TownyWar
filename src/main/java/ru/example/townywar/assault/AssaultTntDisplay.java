package ru.example.townywar.assault;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.util.TimeUtil;

public final class AssaultTntDisplay {

    private final TownyWarPlugin plugin;
    private final NamespacedKey displayKey;

    public AssaultTntDisplay(TownyWarPlugin plugin) {
        this.plugin = plugin;
        this.displayKey = new NamespacedKey(plugin, "assault_tnt_display");
    }

    public NamespacedKey getDisplayKey() { return displayKey; }

    public void spawn(AssaultTnt t) {
        Location loc = textLocation(t);
        if (loc == null) return;
        if (!isChunkLoaded(loc)) return;

        String key = buildKey(t);
        TextDisplay existing = getDisplay(t);
        if (existing != null && existing.isValid()) {
            existing.text(buildText(t));
            t.setLastRenderedKey(key);
            return;
        }

        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(false);
            d.setShadowed(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setViewRange(1.0f);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
            d.text(buildText(t));
        });
        display.text(buildText(t));
        t.setDisplayEntityId(display.getUniqueId());
        t.setLastRenderedKey(key);
    }

    public void update(AssaultTnt t) {
        Location loc = textLocation(t);
        if (loc == null || !isChunkLoaded(loc)) return;
        TextDisplay d = getDisplay(t);
        if (d == null || !d.isValid()) { spawn(t); return; }
        String key = buildKey(t);
        if (key.equals(t.getLastRenderedKey())) return;
        t.setLastRenderedKey(key);
        d.text(buildText(t));
    }

    public void remove(AssaultTnt t) {
        TextDisplay d = getDisplay(t);
        if (d != null && d.isValid()) d.remove();
        t.setDisplayEntityId(null);
        t.setLastRenderedKey(null);
    }

    private Location textLocation(AssaultTnt t) {
        if (t.getWorld() == null) return null;
        World world = Bukkit.getWorld(t.getWorld());
        if (world == null) return null;
        return new Location(world, t.getX() + 0.5, t.getY() + 1.5, t.getZ() + 0.5);
    }

    private boolean isChunkLoaded(Location loc) {
        if (loc.getWorld() == null) return false;
        return loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    private TextDisplay getDisplay(AssaultTnt t) {
        java.util.UUID id = t.getDisplayEntityId();
        if (id == null) return null;
        Entity e = Bukkit.getEntity(id);
        return (e instanceof TextDisplay td) ? td : null;
    }

    private String buildKey(AssaultTnt t) {
        char icon = t.isPaused() ? 'P' : 'R';
        long remainingMs;
        if (t.isPaused()) {
            Integer sec = t.getPausedRemainingSeconds();
            remainingMs = (sec == null ? 0 : sec) * 1000L;
        } else {
            Long finish = t.getDetonateAt();
            remainingMs = finish == null ? 0 : Math.max(0, finish - System.currentTimeMillis());
        }
        long secs = remainingMs / 1000L;
        return icon + ":" + secs + ":" + t.getDurability() + "/" + t.getMaxDurability();
    }

    private Component buildText(AssaultTnt t) {
        String icon = t.isPaused() ? "⏸" : "💥";
        long remainingMs;
        if (t.isPaused()) {
            Integer sec = t.getPausedRemainingSeconds();
            remainingMs = (sec == null ? 0 : sec) * 1000L;
        } else {
            Long finish = t.getDetonateAt();
            remainingMs = finish == null ? 0 : Math.max(0, finish - System.currentTimeMillis());
        }

        NamedTextColor durColor = t.getDurability() > t.getMaxDurability() * 0.6
                ? NamedTextColor.GREEN
                : t.getDurability() > t.getMaxDurability() * 0.3
                ? NamedTextColor.YELLOW : NamedTextColor.RED;

        return Component.text()
                .append(Component.text(icon + " ", NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(TimeUtil.format(remainingMs), NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(t.getDurability() + "/" + t.getMaxDurability(), durColor)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false))
                .build();
    }
}