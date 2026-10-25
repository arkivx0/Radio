package net.programmierecke.radiodroid2.players.exoplayer;

/** Minimal Ogg page parsing for the time-shift ring (RFC 3533). */
final class OggPages {
    static final int MIN_HEADER = 27;

    private OggPages() { }

    static boolean isPage(byte[] b, int i) {
        return b[i] == 'O' && b[i + 1] == 'g' && b[i + 2] == 'g' && b[i + 3] == 'S';
    }

    /** First "OggS" capture pattern (version 0) in [from, end), or -1. */
    static int find(byte[] b, int from, int end) {
        for (int i = from; i + 5 <= end; i++) {
            if (isPage(b, i) && b[i + 4] == 0) return i;
        }
        return -1;
    }

    /** Whole page length at i, or -1 if more bytes are needed. */
    static int pageLength(byte[] b, int i, int end) {
        if (end - i < MIN_HEADER) return -1;
        int segs = b[i + 26] & 0xff;
        if (end - i < MIN_HEADER + segs) return -1;
        int body = 0;
        for (int k = 0; k < segs; k++) body += b[i + MIN_HEADER + k] & 0xff;
        int total = MIN_HEADER + segs + body;
        return end - i < total ? -1 : total;
    }

    static long granule(byte[] b, int i) {
        long g = 0;
        for (int k = 7; k >= 0; k--) g = (g << 8) | (b[i + 6 + k] & 0xff);
        return g;
    }
}
