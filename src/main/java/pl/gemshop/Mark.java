package pl.gemshop;

import java.util.UUID;

/** Globalny cooldown przycisku Bomba (60 s) + aktualnie oznaczony gracz. */
public final class Mark {
    public static final long DURATION = 60_000L;
    public static UUID id;
    public static String name;
    public static long end;

    public static boolean active() { return id != null && System.currentTimeMillis() < end; }
    public static long remaining() { return Math.max(0, end - System.currentTimeMillis()); }
    public static void start(UUID i, String n) { id = i; name = n; end = System.currentTimeMillis() + DURATION; }
    public static void tick() { if (id != null && System.currentTimeMillis() >= end) { id = null; name = null; } }

    public static String fmt(long ms) {
        long sec = (ms + 999) / 1000;
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }
}
