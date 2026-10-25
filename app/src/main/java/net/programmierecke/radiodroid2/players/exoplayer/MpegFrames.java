package net.programmierecke.radiodroid2.players.exoplayer;

/**
 * Radio fork: MPEG audio (MP1/2/3) and AAC ADTS frame headers, used to keep the time-shift ring a
 * clean sequence of whole frames and to start readers on a frame boundary.
 */
final class MpegFrames {
    static final int HEADER = 7; // bytes needed to read any supported header

    private static final int[][] BITRATES = {
            {32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448}, // V1 L1
            {32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384},    // V1 L2
            {32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320},     // V1 L3
            {32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256},    // V2 L1
            {8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},         // V2 L2/L3
    };
    private static final int[] RATES_V1 = {44100, 48000, 32000};
    private static final int[] ADTS_RATES = {96000, 88200, 64000, 48000, 44100, 32000, 24000,
            22050, 16000, 12000, 11025, 8000, 7350};

    private MpegFrames() { }

    /** Length of the frame whose header starts at b[i] (needs HEADER bytes), or -1 if invalid. */
    static int frameLength(byte[] b, int i) {
        int b0 = b[i] & 0xFF, b1 = b[i + 1] & 0xFF, b2 = b[i + 2] & 0xFF;
        if (b0 != 0xFF || (b1 & 0xE0) != 0xE0) return -1;
        int layer = (b1 >> 1) & 3;
        if (layer == 0) { // ADTS (AAC)
            if ((b1 & 0xF6) != 0xF0) return -1;
            if (((b2 >> 2) & 0xF) >= ADTS_RATES.length) return -1;
            int len = ((b[i + 3] & 3) << 11) | ((b[i + 4] & 0xFF) << 3) | ((b[i + 5] & 0xE0) >> 5);
            return len >= 7 ? len : -1;
        }
        int version = (b1 >> 3) & 3; // 0 = 2.5, 1 = reserved, 2 = V2, 3 = V1
        if (version == 1) return -1;
        int brIdx = (b2 >> 4) & 0xF, srIdx = (b2 >> 2) & 3, pad = (b2 >> 1) & 1;
        if (brIdx == 0 || brIdx == 15 || srIdx == 3) return -1;
        int l = 4 - layer; // 1, 2, 3
        boolean v1 = version == 3;
        int table = v1 ? l - 1 : (l == 1 ? 3 : 4);
        int bitrate = BITRATES[table][brIdx - 1] * 1000;
        int rate = RATES_V1[srIdx] >> (v1 ? 0 : version == 2 ? 1 : 2);
        if (l == 1) return (12 * bitrate / rate + pad) * 4;
        if (l == 3 && !v1) return 72 * bitrate / rate + pad;
        return 144 * bitrate / rate + pad;
    }

    /** True when two headers belong to the same stream (same kind, version, layer, rate). */
    static boolean sameStream(byte[] b, int i, int j) {
        if (((b[i + 1] ^ b[j + 1]) & 0xFE) != 0) return false;
        int mask = ((b[i + 1] >> 1) & 3) == 0 ? 0xFC : 0x0C; // ADTS: profile+rate; MPEG: rate
        return ((b[i + 2] ^ b[j + 2]) & mask) == 0;
    }

    /**
     * First offset in b[from, end) where a frame starts that is followed by a second valid frame
     * of the same stream, or -1.
     */
    static int findSync(byte[] b, int from, int end) {
        for (int i = from; i + HEADER <= end; i++) {
            int len = frameLength(b, i);
            if (len <= 0) continue;
            int j = i + len;
            if (j + HEADER > end) continue;
            if (frameLength(b, j) > 0 && sameStream(b, i, j)) return i;
        }
        return -1;
    }
}
