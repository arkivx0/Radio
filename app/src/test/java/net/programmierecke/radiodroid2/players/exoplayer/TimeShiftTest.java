package net.programmierecke.radiodroid2.players.exoplayer;

import android.net.Uri;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

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
        r.write(6, data, 0, 8);
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
        assertEquals(5 * 60 * 16000L, TimeShiftSettings.bytesFor(5, 128));
        assertEquals(TimeShiftSettings.bytesFor(1, 128), TimeShiftSettings.bytesFor(1, 0));
        int cap30 = TimeShiftSettings.capacityFor("30", 320);
        assertTrue(cap30 > 70_000_000 && cap30 <= TimeShiftSettings.MAX_BYTES);
        assertTrue(TimeShiftSettings.useDisk(cap30));
        assertFalse(TimeShiftSettings.useDisk(TimeShiftSettings.capacityFor("5", 128)));
        assertTrue(TimeShiftSettings.capacityFor("auto", 1411) <= TimeShiftSettings.AUTO_CAP_BYTES);
        assertEquals(5 * 60_000L, TimeShiftSettings.maxBehindMsFor("5", 128));
        assertEquals(5, TimeShiftSettings.minutesFor("garbage"));
        assertEquals(30, TimeShiftSettings.minutesFor("30"));
    }

    @Test
    void decoderBitrateOverridesDirectoryBitrate() {
        TimeShiftBuffer ts = new TimeShiftBuffer(() -> null, Uri.EMPTY, new RingStore.Memory(1000), 60_000, 128);
        assertEquals(16000, ts.bytesPerSecond(), 0.5);
        ts.setBitrateKbps(64);
        assertEquals(8000, ts.bytesPerSecond(), 0.5);
    }

    // ---- Simulation: server sends a 20 s burst then 2x realtime; a fake player reads ahead ----

    private static final int BPS = 16000;          // 128 kbps
    private static final int STEP_MS = 100;

    private static class Sim {
        long now;
        final TimeShiftBuffer ts;
        long serverPos;                 // bytes sent by the server
        long prepareTime;
        DataSource reader;
        final long readAheadMs;
        final byte[] buf = new byte[8192];
        long maxReadAhead;

        Sim(int ringBytes, long readAheadMs) {
            this.readAheadMs = readAheadMs;
            ts = new TimeShiftBuffer(() -> null, Uri.EMPTY, new RingStore.Memory(ringBytes), 300_000, 128, () -> now);
        }

        long played() { return now - prepareTime; }

        void prepare() throws IOException {
            prepareTime = now;
            reader = ts.readerFactory().createDataSource();
            reader.open(new DataSpec(Uri.EMPTY));
        }

        /** The server offers `bytes`; the ring takes what it has room for (backpressure). */
        void serve(long bytes) throws Exception {
            while (bytes > 0) {
                long room = ts.room();
                if (room == 0) return; // network read paused
                int n = (int) Math.min(Math.min(bytes, room), 4096);
                byte[] b = new byte[n];
                for (int i = 0; i < n; i++) b[i] = (byte) ((serverPos + i) % 251);
                ts.write(b, n);
                serverPos += n;
                bytes -= n;
            }
        }

        /** Player reads until it is readAheadMs ahead of what it plays; checks every byte. */
        void playerLoad() throws IOException {
            long goal = ts.playhead(played()) + readAheadMs * BPS / 1000;
            while (ts.readerPos() < goal && ts.written() > ts.readerPos()) {
                long from = ts.readerPos();
                int want = (int) Math.min(buf.length, goal - from);
                int n = reader.read(buf, 0, want);
                for (int i = 0; i < n; i++)
                    if (buf[i] != (byte) ((from + i) % 251))
                        throw new AssertionError("overwritten/corrupt byte at " + (from + i));
            }
            maxReadAhead = Math.max(maxReadAhead, ts.written() - ts.readerPos());
        }

        void run(long ms) throws Exception {
            for (long t = 0; t < ms; t += STEP_MS) {
                now += STEP_MS;
                serve(2L * BPS * STEP_MS / 1000); // 2x realtime
                playerLoad();
            }
        }
    }

    @Test
    void tapsMoveFiveSecondsFromPlayheadAndGoLiveReturnsToZero() throws Exception {
        Sim sim = new Sim(1_200_000, 10_000);   // ~75 s ring
        sim.prepare();                            // start at live
        sim.serve(20L * BPS);                     // 20 s opening burst
        sim.run(30_000);

        assertTrue(sim.ts.behindLiveMs(sim.played()) < 600,
                "before any tap the offset must be ~0, was " + sim.ts.behindLiveMs(sim.played()));
        assertTrue(sim.ts.historyMs(sim.played()) >= 29_000, "history " + sim.ts.historyMs(sim.played()));

        long expected = 0;
        for (int tap = 1; tap <= 4; tap++) {
            long head = sim.ts.playhead(sim.played());
            long label = sim.ts.shift(sim.played(), 5000);
            expected += 5000;
            sim.prepare();
            assertEquals(head - 5L * BPS, sim.ts.playhead(sim.played()), "tap " + tap + " target");
            assertTrue(Math.abs(label - expected) <= 600, "tap " + tap + " label " + label);
            sim.run(2000);
            long shown = sim.ts.behindLiveMs(sim.played());
            assertTrue(Math.abs(shown - expected) <= 600, "tap " + tap + " shown " + shown + " expected " + expected);
        }

        long label = sim.ts.shift(sim.played(), Long.MIN_VALUE);
        sim.prepare();
        sim.run(2000);
        assertTrue(label < 600, "go live label " + label);
        assertTrue(sim.ts.behindLiveMs(sim.played()) < 600, "after go live " + sim.ts.behindLiveMs(sim.played()));
    }

    @Test
    void smallRingNeverOverwritesUnreadData() throws Exception {
        int ring = 200_000;                      // ~12 s
        Sim sim = new Sim(ring, 60_000);         // greedy player wants 60 s ahead
        sim.prepare();
        sim.serve(20L * BPS);                    // burst bigger than the ring
        sim.run(5000);
        long head = sim.ts.playhead(sim.played());
        sim.ts.shift(sim.played(), 30_000);      // jump to the oldest available
        sim.prepare();
        sim.run(20_000);                         // playerLoad checks every byte read
        assertTrue(sim.maxReadAhead <= sim.ts.maxReadAhead() && sim.ts.maxReadAhead() <= ring - TimeShiftBuffer.MARGIN,
                "unread bytes " + sim.maxReadAhead + " exceed ring");
        assertTrue(sim.ts.playhead(sim.played()) > head - 30L * BPS);
    }
}
