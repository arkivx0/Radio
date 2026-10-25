package net.programmierecke.radiodroid2.players.exoplayer;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeShiftTest {

    @Test
    void memoryRingWrapsAround() throws IOException {
        RingStore r = new RingStore.Memory(10);
        byte[] data = {1, 2, 3, 4, 5, 6, 7, 8};
        r.write(6, data, 0, 8); // positions 6..13 -> wraps
        byte[] out = new byte[8];
        r.read(6, out, 0, 8);
        assertArrayEquals(data, out);
    }

    @Test
    void diskRingWrapsAroundAndDeletesFile() throws IOException {
        File f = File.createTempFile("timeshift", ".buf");
        RingStore r = new RingStore.Disk(f, 10);
        byte[] data = {9, 8, 7, 6, 5, 4, 3};
        r.write(15, data, 0, 7);
        byte[] out = new byte[7];
        r.read(15, out, 0, 7);
        assertArrayEquals(data, out);
        r.close();
        assertFalse(f.exists());
    }

    @Test
    void bufferSizingInMinutes() {
        // 5 min at 128 kbps = 4.8 MB (+ margin)
        assertEquals(5 * 60 * 16000L, TimeShiftSettings.bytesFor(5, 128));
        assertEquals(TimeShiftSettings.bytesFor(1, 128), TimeShiftSettings.bytesFor(1, 0)); // fallback 128
        // 30 min at 320 kbps is ~72 MB and goes to disk
        int cap30 = TimeShiftSettings.capacityFor("30", 320);
        assertTrue(cap30 > 70_000_000 && cap30 <= TimeShiftSettings.MAX_BYTES);
        assertTrue(TimeShiftSettings.useDisk(cap30));
        // default 5 min at 128 kbps stays in RAM
        assertFalse(TimeShiftSettings.useDisk(TimeShiftSettings.capacityFor("5", 128)));
        // Auto is capped
        assertTrue(TimeShiftSettings.capacityFor("auto", 1411) <= TimeShiftSettings.AUTO_CAP_BYTES);
        assertEquals(5 * 60_000L, TimeShiftSettings.maxBehindMsFor("5", 128));
        assertEquals(5, TimeShiftSettings.minutesFor("garbage"));
        assertEquals(30, TimeShiftSettings.minutesFor("30"));
    }

    /** Endless fake stream: byte value = (absolute position % 251). */
    private static class FakeStream implements DataSource {
        long pos;

        @Override public void addTransferListener(TransferListener transferListener) { }
        @Override public long open(DataSpec dataSpec) { pos = 0; return C.LENGTH_UNSET; }
        @Override public int read(byte[] b, int off, int len) {
            try { Thread.sleep(2); } catch (InterruptedException e) { return C.RESULT_END_OF_INPUT; }
            int n = Math.min(len, 500);
            for (int i = 0; i < n; i++) b[off + i] = (byte) ((pos + i) % 251);
            pos += n;
            return n;
        }
        @Override public Uri getUri() { return null; }
        @Override public void close() { }
    }

    @Test
    void backFiveSecondsStartsFiveSecondsBehindLiveEvenIfReopened() throws Exception {
        // 8 kbps = 1000 bytes per second, so 5 s = 5000 bytes
        TimeShiftBuffer ts = new TimeShiftBuffer(FakeStream::new, Uri.EMPTY, new RingStore.Memory(200_000), 60_000, 8);
        ts.start();
        long t0 = System.currentTimeMillis();
        while (ts.maxBehindMs() < 20_000 && System.currentTimeMillis() - t0 < 10_000) Thread.sleep(20);
        assertTrue(ts.maxBehindMs() >= 20_000, "buffer did not fill");

        long behind = ts.seekBehind(5000);
        assertTrue(behind >= 4900 && behind <= 5200, "behind=" + behind);

        DataSource reader = ts.readerFactory().createDataSource();
        reader.open(new DataSpec(Uri.EMPTY));
        long readerBehind = ts.readerBehindMs();
        assertTrue(readerBehind >= 4900, "reader should start ~5 s behind, was " + readerBehind);
        // ExoPlayer may open the source again for the same prepare: it must keep the same start
        DataSource again = ts.readerFactory().createDataSource();
        again.open(new DataSpec(Uri.EMPTY));
        assertTrue(ts.readerBehindMs() >= 4800, "reopen jumped to live");

        // Go live
        assertEquals(0, ts.seekBehind(0));
        ts.stop();
    }
}
