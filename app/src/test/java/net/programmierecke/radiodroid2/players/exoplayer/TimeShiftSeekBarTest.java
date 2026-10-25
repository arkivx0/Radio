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
        assertEquals(10_000, ts.spanMs(played), 50);
        assertEquals(10_000, ts.positionInSpanMs(played), 50); // at live: end of the bar
        assertEquals(Long.MIN_VALUE, ts.deltaForSpanPosition(played, 10_000));
        assertEquals(Long.MIN_VALUE, ts.deltaForSpanPosition(played, 99_999)); // clamped
        assertEquals(6_000, ts.deltaForSpanPosition(played, 4_000), 50);       // 6 s back
        assertEquals(10_000, ts.deltaForSpanPosition(played, -5), 50);          // clamped to start
        long off = ts.shift(played, ts.deltaForSpanPosition(played, 4_000));
        assertEquals(6_000, off, 50);
        assertEquals(4_000, ts.positionInSpanMs(0), 50);
        assertTrue(ts.spanMs(0) >= 10_000);
    }
}
