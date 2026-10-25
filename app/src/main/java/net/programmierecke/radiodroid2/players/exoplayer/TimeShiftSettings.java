package net.programmierecke.radiodroid2.players.exoplayer;

/** Radio Arkiv: skip-back buffer sizing (pure maths, unit-tested). */
public final class TimeShiftSettings {
    private TimeShiftSettings() {
    }

    public static final String PREF_KEY = "timeshift_minutes";
    public static final String AUTO = "auto";
    public static final String DEFAULT = "5";
    public static final int FALLBACK_KBPS = 128;
    /** Buffers above this go to a cache file instead of RAM. */
    public static final int MAX_RAM_BYTES = 16 * 1024 * 1024;
    /** Auto: 5 minutes, but never more than this. */
    public static final int AUTO_CAP_BYTES = 16 * 1024 * 1024;
    /** Absolute cap (30 min at 320 kbps is ~72 MB). */
    public static final int MAX_BYTES = 80 * 1024 * 1024;
    private static final int MARGIN_BYTES = 256 * 1024;

    public static int minutesFor(String pref) {
        if (pref == null || AUTO.equals(pref)) return 5;
        try {
            int m = Integer.parseInt(pref.trim());
            return Math.max(1, Math.min(30, m));
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    public static int effectiveKbps(int kbps) {
        return kbps > 0 ? Math.min(kbps, 1536) : FALLBACK_KBPS;
    }

    public static long bytesFor(int minutes, int kbps) {
        return (long) minutes * 60L * effectiveKbps(kbps) * 1000L / 8L;
    }

    /** Ring capacity in bytes for a preference value and stream bitrate. */
    public static int capacityFor(String pref, int kbps) {
        long bytes = bytesFor(minutesFor(pref), kbps) + MARGIN_BYTES;
        long cap = AUTO.equals(pref) ? AUTO_CAP_BYTES : MAX_BYTES;
        return (int) Math.max(1024 * 1024, Math.min(bytes, cap));
    }

    /** Longest skip back the buffer can hold, in ms. */
    public static long maxBehindMsFor(String pref, int kbps) {
        int cap = capacityFor(pref, kbps) - MARGIN_BYTES;
        long ms = cap * 8000L / (effectiveKbps(kbps) * 1000L);
        return Math.min(ms, minutesFor(pref) * 60_000L);
    }

    public static boolean useDisk(int capacityBytes) {
        return capacityBytes > MAX_RAM_BYTES;
    }

    /** Human summary like "about 12 MB at 320 kbps". */
    public static String approxMb(String pref, int kbps) {
        double mb = capacityFor(pref, kbps) / (1024.0 * 1024.0);
        return mb < 10 ? String.format(java.util.Locale.US, "%.1f MB", mb) : Math.round(mb) + " MB";
    }

}
