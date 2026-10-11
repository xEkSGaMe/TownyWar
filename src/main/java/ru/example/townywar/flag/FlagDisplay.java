package ru.example.townywar.flag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.example.townywar.TownyWarPlugin;
import ru.example.townywar.util.TimeUtil;

public final class FlagDisplay {

    private final TownyWarPlugin plugin;
    private final NamespacedKey displayKey;

    public FlagDisplay(TownyWarPlugin plugin) {
        this.plugin = plugin;
        this.displayKey = new NamespacedKey(plugin, "flag_display");
    }

    public NamespacedKey getDisplayKey() { return displayKey; }

    /**
     * Создаёт или обновляет TextDisplay для флага. После вызова
     * {@link PlacedFlag#getLastRenderedKey()} синхронизирован с содержимым
     * дисплея — следующий {@link #update(PlacedFlag)} не будет слать пакет,
     * если состояние не изменилось.
     */
    public void spawn(PlacedFlag flag) {
        Location loc = textLocation(flag);
        if (loc == null) return;
        if (!isChunkLoaded(loc)) return;

        String key = buildKey(flag);

        TextDisplay existing = getDisplay(flag);
        if (existing != null && existing.isValid()) {
            existing.text(buildText(flag));
            flag.setLastRenderedKey(key);
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
            d.text(buildText(flag));
        });
        display.text(buildText(flag));
        flag.setDisplayEntityId(display.getUniqueId());
        flag.setLastRenderedKey(key);
    }

    /**
     * Обновляет текст дисплея только если состояние изменилось (иконка/секунды/прочность).
     * Иначе — no-op, чтобы не гнать metadata-пакет при 300+ флагах каждую секунду впустую.
     */
    public void update(PlacedFlag flag) {
        Location loc = textLocation(flag);
        if (loc == null || !isChunkLoaded(loc)) return;

        TextDisplay d = getDisplay(flag);
        if (d == null || !d.isValid()) {
            // Дисплея нет (не спавнился / слетел) — создаём заново. spawn сам выставит ключ.
            spawn(flag);
            return;
        }

        String key = buildKey(flag);
        if (key.equals(flag.getLastRenderedKey())) return;

        flag.setLastRenderedKey(key);
        d.text(buildText(flag));
    }

    public void remove(PlacedFlag flag) {
        TextDisplay d = getDisplay(flag);
        if (d != null && d.isValid()) d.remove();
        flag.setDisplayEntityId(null);
        flag.setLastRenderedKey(null);
    }

    public void playPlaceEffect(Location center) {
        if (center == null || center.getWorld() == null) return;
        final int[] elapsed = {0};
        new BukkitRunnable() {
            @Override public void run() {
                if (elapsed[0] >= 60 || center.getWorld() == null) { cancel(); return; }
                for (int i = 0; i < 3; i++) {
                    double angle = Math.random() * 2 * Math.PI;
                    double radius = 0.8 + Math.random() * 0.6;
                    double x = center.getX() + 0.5 + Math.cos(angle) * radius;
                    double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
                    double y = center.getY() + 0.2 + Math.random() * 1.6;
                    center.getWorld().spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
                }
                elapsed[0] += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
        center.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
    }

    public void playBreakEffect(Location center) {
        if (center == null || center.getWorld() == null) return;
        center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center.getX() + 0.5, center.getY() + 0.8, center.getZ() + 0.5, 15, 0.3, 0.3, 0.3, 0.02);
        center.getWorld().spawnParticle(Particle.CRIT, center.getX() + 0.5, center.getY() + 0.8, center.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.1);
        center.getWorld().playSound(center, Sound.BLOCK_ANVIL_BREAK, 1.0f, 0.8f);
    }

    public void playHitEffect(Location center) {
        if (center == null || center.getWorld() == null) return;
        center.getWorld().spawnParticle(Particle.CRIT, center.getX() + 0.5, center.getY() + 0.8, center.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.05);
        center.getWorld().playSound(center, Sound.BLOCK_WOOD_HIT, 1.0f, 0.9f);
    }

    public void playCaptureEffect(Location center) {
        if (center == null || center.getWorld() == null) return;
        center.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center.getX() + 0.5, center.getY() + 0.8, center.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.05);
        center.getWorld().spawnParticle(Particle.END_ROD, center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5, 15, 0.4, 0.4, 0.4, 0.05);
        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        center.getWorld().playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
    }

    private Location textLocation(PlacedFlag flag) {
        if (flag.getWorld() == null) return null;
        World world = Bukkit.getWorld(flag.getWorld());
        if (world == null) return null;
        return new Location(world, flag.getX() + 0.5, flag.getY() + 2.0, flag.getZ() + 0.5);
    }

    private boolean isChunkLoaded(Location loc) {
        if (loc.getWorld() == null) return false;
        return loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    private TextDisplay getDisplay(PlacedFlag flag) {
        java.util.UUID id = flag.getDisplayEntityId();
        if (id == null) return null;
        Entity e = Bukkit.getEntity(id);
        return (e instanceof TextDisplay td) ? td : null;
    }

    /**
     * Строит строковый ключ, отражающий <b>отображаемое</b> содержимое:
     * иконку (пауза/бег), округлённые секунды и прочность. Сравнивается
     * со {@link PlacedFlag#getLastRenderedKey()} перед отправкой пакета.
     * <p>
     * Секунды — именно целые, а не миллисекунды: форматтер {@link TimeUtil#format(long)}
     * тоже режет до секунд, поэтому ключ меняется ровно тогда, когда меняется картинка.
     */
    private String buildKey(PlacedFlag flag) {
        char icon = flag.isPaused() ? 'P' : 'R';
        long remainingMs;
        if (flag.isPaused()) {
            Integer sec = flag.getPausedRemainingSeconds();
            remainingMs = (sec == null ? 0 : sec) * 1000L;
        } else {
            Long finish = flag.getCaptureFinishAt();
            remainingMs = finish == null ? 0 : Math.max(0, finish - System.currentTimeMillis());
        }
        long secs = remainingMs / 1000L;
        return icon + ":" + secs + ":" + flag.getDurability() + "/" + flag.getMaxDurability();
    }

    private Component buildText(PlacedFlag flag) {
        String icon = flag.isPaused() ? "⏸" : "⏳";
        long remainingMs;
        if (flag.isPaused()) {
            Integer sec = flag.getPausedRemainingSeconds();
            remainingMs = (sec == null ? 0 : sec) * 1000L;
        } else {
            Long finish = flag.getCaptureFinishAt();
            remainingMs = finish == null ? 0 : Math.max(0, finish - System.currentTimeMillis());
        }

        NamedTextColor durColor = flag.getDurability() > flag.getMaxDurability() * 0.6
                ? NamedTextColor.GREEN
                : flag.getDurability() > flag.getMaxDurability() * 0.3
                ? NamedTextColor.YELLOW : NamedTextColor.RED;

        return Component.text()
                .append(Component.text(icon + " ", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(TimeUtil.format(remainingMs), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(flag.getDurability() + "/" + flag.getMaxDurability(), durColor)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false))
                .build();
    }
}