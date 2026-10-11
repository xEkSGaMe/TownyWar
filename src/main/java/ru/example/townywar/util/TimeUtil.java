package ru.example.townywar.util;

public final class TimeUtil {

    private TimeUtil() { }

    /** Форматирует миллисекунды как {@code MM:SS} или {@code H:MM:SS}. */
    public static String format(long ms) {
        if (ms <= 0) return "0:00";
        long totalSec = ms / 1000L;
        long hours = totalSec / 3600L;
        long minutes = (totalSec % 3600L) / 60L;
        long seconds = totalSec % 60L;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }
}