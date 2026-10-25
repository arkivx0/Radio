package net.programmierecke.radiodroid2.players.exoplayer;

import android.net.Uri;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Media session seek bar: span, playhead within it, and seeking to a span position. */
class TimeShiftSeekBarTest {
    private long now = 1_000_000;

    @Test
    void spanPositionAndSeek() throws Exception {
        // 128 kbps = 16000 B/s, ring big enough for ~60 s, max offset 30 s
        TimeShiftBuffer ts = new TimeShiftBuffer(() -> null, Uri.EMPTY,
                new RingStore.Memory(60 * 16000 + TimeShiftBuffer.MARGIN), 30_000, 128, () -> now);
        DataSource r = ts.readerFactory().createDataSource();
        r.open(new DataSpec(Uri.EMPTY)); // first start: live at 0
        ts.onPlaybackStarted(0);
        byte[] b = new byte[16000];
        byte[] sink = new byte[16000];
        for (int s = 0; s < 10; s++) { // 10 s of realtime playback, reader keeps up
            ts.write(b, b.length);
            r.read(sink, 0, sink.length);
            now += 1000;
        }
        long played = 10_000;
        // fixed bar = buffer size (30 s); right end = live
        assertEquals(30_000, ts.barMs());
        assertEquals(10_000, ts.availableMs(played), 50);
        assertEquals(30_000, ts.barPositionMs(played)); // live: pinned at the end
        assertEquals(Long.MIN_VALUE, ts.deltaForBarPosition(played, 30_000));
        assertEquals(Long.MIN_VALUE, ts.deltaForBarPosition(played, 99_999)); // clamped
        assertEquals(Long.MIN_VALUE, ts.deltaForBarPosition(played, 29_500)); // within 1 s of live
        assertEquals(6_000, ts.deltaForBarPosition(played, 24_000), 50);       // 6 s back
        assertEquals(10_000, ts.deltaForBarPosition(played, 0), 50);           // clamped to available audio
        long off = ts.shift(played, ts.deltaForBarPosition(played, 24_000));
        assertEquals(6_000, off, 50);
        assertEquals(24_000, ts.barPositionMs(0), 50);
        // playing on behind live: thumb stays put (live and playhead move together)
        r.open(new DataSpec(Uri.EMPTY));
        for (int s = 0; s < 5; s++) {
            ts.write(b, b.length);
            r.read(sink, 0, sink.length);
            now += 1000;
        }
        assertEquals(24_000, ts.barPositionMs(5_000), 50);
        assertEquals(30_000, ts.barMs()); // length never rescales
    }
}
