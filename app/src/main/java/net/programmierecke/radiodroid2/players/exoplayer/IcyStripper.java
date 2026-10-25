package net.programmierecke.radiodroid2.players.exoplayer;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Radio fork: removes Shoutcast/Icecast in-band metadata blocks (icy-metaint) from a stream so
 * only audio reaches the time-shift ring. ExoPlayer strips these itself only when it talks to the
 * server directly; through the ring it would decode them as audio (chirps, decoder errors).
 */
final class IcyStripper {
    private final int metaInt;
    private int untilMeta;       // audio bytes before the next metadata length byte
    private int metaLeft = -1;   // metadata bytes still to skip; -1 = expecting audio

    IcyStripper(int metaInt) {
        this.metaInt = metaInt;
        this.untilMeta = metaInt;
    }

    /** metaint from response headers (names may be any case), or 0 when there is none. */
    static int metaIntFrom(@Nullable Map<String, List<String>> headers) {
        if (headers == null) return 0;
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase("icy-metaint")
                    && e.getValue() != null && !e.getValue().isEmpty()) {
                try {
                    return Math.max(0, Integer.parseInt(e.getValue().get(0).trim()));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /** Compacts buf[off, off+n) in place to audio only; returns the audio byte count. */
    int strip(byte[] buf, int off, int n) {
        if (metaInt <= 0) return n;
        int in = off, out = off, end = off + n;
        while (in < end) {
            if (metaLeft > 0) {
                int skip = Math.min(metaLeft, end - in);
                in += skip;
                metaLeft -= skip;
                if (metaLeft == 0) { metaLeft = -1; untilMeta = metaInt; }
            } else if (untilMeta == 0) {
                metaLeft = (buf[in++] & 0xFF) * 16;
                if (metaLeft == 0) { metaLeft = -1; untilMeta = metaInt; }
            } else {
                int take = Math.min(untilMeta, end - in);
                if (in != out) System.arraycopy(buf, in, buf, out, take);
                in += take;
                out += take;
                untilMeta -= take;
            }
        }
        return out - off;
    }
}
