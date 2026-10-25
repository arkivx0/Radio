package net.programmierecke.radiodroid2.players.exoplayer;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real MP3 frames + ICY metadata + ID3 + a reconnect, through the feeder, ring and reader. */
class TimeShiftFramingTest {
    static final int FRAME = 417;      // MPEG-1 Layer III, 128 kbps, 44.1 kHz, no padding
    static final int METAINT = 8000;

    /** Frame i: valid header + pseudo-random payload; byte 4..7 carry the frame number. */
    static byte[] frame(int i) {
        byte[] f = new byte[FRAME];
        new Random(i).nextBytes(f);
        f[0] = (byte) 0xFF; f[1] = (byte) 0xFB; f[2] = (byte) 0x90; f[3] = (byte) 0x64;
        f[4] = (byte) (i >> 24); f[5] = (byte) (i >> 16); f[6] = (byte) (i >> 8); f[7] = (byte) i;
        return f;
    }

    static byte[] id3() {
        byte[] t = new byte[10 + 300];
        t[0] = 'I'; t[1] = 'D'; t[2] = '3'; t[3] = 3; t[9] = (byte) 300;
        return t;
    }

    /** Audio from frame `from` (cut after `cutBytes` audio bytes if >= 0), with ICY interleaving. */
    static byte[] connection(int from, int frames, int cutBytes) {
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        byte[] tag = id3();
        audio.write(tag, 0, tag.length);
        for (int i = from; i < from + frames; i++) audio.write(frame(i), 0, FRAME);
        byte[] a = audio.toByteArray();
        int n = cutBytes >= 0 ? cutBytes : a.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int block = 0;
        for (int p = 0; p < n; p += METAINT) {
            int len = Math.min(METAINT, n - p);
            out.write(a, p, len);
            if (len == METAINT) {
                if (block++ % 2 == 0) {
                    byte[] meta = new byte[1 + 32];
                    meta[0] = 2;
                    byte[] s = "StreamTitle='Chirp';".getBytes(StandardCharsets.US_ASCII);
                    System.arraycopy(s, 0, meta, 1, s.length);
                    out.write(meta, 0, meta.length);
                } else {
                    out.write(0); // empty metadata block
                }
            }
        }
        return out.toByteArray();
    }

    /** Fake server: serves the given connections in random chunk sizes, then blocks. */
    static class FakeServer implements DataSource.Factory {
        final List<byte[]> conns;
        int next;
        FakeServer(List<byte[]> conns) { this.conns = conns; }

        @NonNull @Override public synchronized DataSource createDataSource() {
            final byte[] data = next < conns.size() ? conns.get(next++) : null;
            return new DataSource() {
                int pos;
                final Random rnd = new Random(7);
                @Override public void addTransferListener(@NonNull TransferListener l) { }
                @Override public long open(@NonNull DataSpec dataSpec) { return C.LENGTH_UNSET; }
                @Override public int read(@NonNull byte[] b, int off, int len) throws IOException {
                    if (data == null) {
                        try { Thread.sleep(60_000); } catch (InterruptedException e) { throw new java.io.InterruptedIOException(); }
                        return C.RESULT_END_OF_INPUT;
                    }
                    if (pos >= data.length) return C.RESULT_END_OF_INPUT;
                    int n = Math.min(Math.min(len, 1 + rnd.nextInt(5000)), data.length - pos);
                    System.arraycopy(data, pos, b, off, n);
                    pos += n;
                    return n;
                }
                @NonNull @Override public Map<String, List<String>> getResponseHeaders() {
                    return Collections.singletonMap("Icy-MetaInt", Collections.singletonList("" + METAINT));
                }
                @Override public Uri getUri() { return Uri.EMPTY; }
                @Override public void close() { }
            };
        }
    }

    /** Asserts out is whole valid frames (last may be cut); returns the frame numbers. */
    static List<Integer> assertFrames(byte[] out, int len) {
        List<Integer> ids = new ArrayList<>();
        int p = 0;
        while (p + FRAME <= len) {
            assertEquals(FRAME, MpegFrames.frameLength(out, p), "no frame header at " + p);
            int id = ((out[p + 4] & 0xFF) << 24) | ((out[p + 5] & 0xFF) << 16) | ((out[p + 6] & 0xFF) << 8) | (out[p + 7] & 0xFF);
            byte[] want = frame(id);
            for (int k = 0; k < FRAME; k++)
                if (out[p + k] != want[k]) throw new AssertionError("frame " + id + " corrupted at byte " + k);
            ids.add(id);
            p += FRAME;
        }
        return ids;
    }

    static int readAll(DataSource r, TimeShiftBuffer ts, byte[] out, long untilWritten, long timeoutMs, int delayEvery) throws Exception {
        int len = 0;
        long end = System.currentTimeMillis() + timeoutMs;
        int reads = 0;
        while (System.currentTimeMillis() < end && len < out.length) {
            if (ts.readerPos() >= untilWritten && ts.written() >= untilWritten) break;
            if (ts.written() <= ts.readerPos()) { Thread.sleep(5); continue; }
            int n = r.read(out, len, Math.min(3000, out.length - len));
            if (n > 0) len += n;
            if (delayEvery > 0 && ++reads % delayEvery == 0) Thread.sleep(2);
        }
        return len;
    }

    static long expectedAudioBytes(int frames) { return (long) frames * FRAME; }

    @Test
    void stripperRemovesIcyBlocks() {
        byte[] raw = connection(0, 60, -1);
        IcyStripper s = new IcyStripper(METAINT);
        byte[] copy = raw.clone();
        int n = s.strip(copy, 0, copy.length);
        assertEquals(id3().length + 60 * FRAME, n);
        // in odd-sized pieces
        IcyStripper s2 = new IcyStripper(METAINT);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (int p = 0; p < raw.length; p += 777) {
            byte[] piece = java.util.Arrays.copyOfRange(raw, p, Math.min(raw.length, p + 777));
            int m = s2.strip(piece, 0, piece.length);
            o.write(piece, 0, m);
        }
        assertTrue(java.util.Arrays.equals(java.util.Arrays.copyOf(copy, n), o.toByteArray()));
        assertEquals(METAINT, IcyStripper.metaIntFrom(Collections.singletonMap("icy-metaint", Collections.singletonList("8000"))));
    }

    @Test
    void readerGetsOnlyWholeFramesAcrossMetadataId3ReconnectAndSkips() throws Exception {
        // connection 1 ends in the middle of frame 120; connection 2 starts with ID3 at frame 300
        List<byte[]> conns = new ArrayList<>();
        conns.add(connection(0, 200, 310 + 120 * FRAME + 200));
        conns.add(connection(300, 200, -1));
        TimeShiftBuffer ts = new TimeShiftBuffer(new FakeServer(conns), Uri.EMPTY,
                new RingStore.Memory(4_000_000), 300_000, 128);
        DataSource r = ts.readerFactory().createDataSource();
        r.open(new DataSpec(Uri.EMPTY));
        ts.start();
        byte[] out = new byte[1_000_000];
        long total = expectedAudioBytes(120 + 199); // frame 119 kept, 120 cut; last frame awaits a next header
        int len = readAll(r, ts, out, total, 5000, 0);
        List<Integer> ids = assertFrames(out, len);

        assertTrue(ids.contains(119) && !ids.contains(120) && ids.contains(300), "frames " + ids.size());
        for (int k = 1; k < ids.size(); k++) {
            int a = ids.get(k - 1), b = ids.get(k);
            assertTrue(b == a + 1 || (a == 119 && b == 300), "gap " + a + " -> " + b);
        }

        // skip back an arbitrary amount: the reader must start on a frame boundary
        for (long back : new long[]{1234, 5000, 777}) {
            ts.shift(0, back);
            DataSource r2 = ts.readerFactory().createDataSource();
            r2.open(new DataSpec(Uri.EMPTY));
            byte[] o2 = new byte[200_000];
            int l2 = readAll(r2, ts, o2, ts.written(), 3000, 0);
            assertTrue(l2 > FRAME, "read " + l2);
            assertFrames(o2, l2);
        }
        // go live
        ts.shift(0, Long.MIN_VALUE);
        ts.stop();
    }

    @Test
    void backpressureWithSmallRingKeepsFramesIntact() throws Exception {
        List<byte[]> conns = new ArrayList<>();
        conns.add(connection(0, 1500, -1)); // ~625 KB, ring is 200 KB
        TimeShiftBuffer ts = new TimeShiftBuffer(new FakeServer(conns), Uri.EMPTY,
                new RingStore.Memory(200_000), 300_000, 128);
        DataSource r = ts.readerFactory().createDataSource();
        r.open(new DataSpec(Uri.EMPTY));
        ts.start();
        byte[] out = new byte[800_000];
        int len = readAll(r, ts, out, expectedAudioBytes(1499), 15000, 3); // slow reader
        List<Integer> ids = assertFrames(out, len);
        assertEquals(1499, ids.size(), "all frames but the last (awaiting its successor)");
        for (int k = 0; k < ids.size(); k++) assertEquals(k, (int) ids.get(k));
        ts.stop();
    }

    @Test
    void recoverRestartsOnFrameBoundary() throws Exception {
        List<byte[]> conns = new ArrayList<>();
        conns.add(connection(0, 400, -1));
        TimeShiftBuffer ts = new TimeShiftBuffer(new FakeServer(conns), Uri.EMPTY,
                new RingStore.Memory(1_000_000), 300_000, 128);
        DataSource r = ts.readerFactory().createDataSource();
        r.open(new DataSpec(Uri.EMPTY));
        ts.start();
        byte[] out = new byte[400_000];
        readAll(r, ts, out, expectedAudioBytes(399), 5000, 0);
        ts.recover(3333);
        DataSource r2 = ts.readerFactory().createDataSource();
        r2.open(new DataSpec(Uri.EMPTY));
        int l2 = readAll(r2, ts, out, ts.written(), 3000, 0);
        List<Integer> ids = assertFrames(out, l2);
        assertTrue(!ids.isEmpty() && ids.get(0) > 100, "restart frame " + (ids.isEmpty() ? -1 : ids.get(0)));
        ts.stop();
    }
}
