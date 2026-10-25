package net.programmierecke.radiodroid2.players.exoplayer;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: Ogg/Opus streams (e.g. an .opus Icecast stream) are not MPEG/ADTS. The framer
 * used to keep only the last 8 KiB while detecting, so the Opus header pages were lost and any
 * reader start that was not the very first byte (Back 5s, decoder-error recovery at +250 ms)
 * began mid-page. ExoPlayer could not recognise the data and retried forever (BUFFERING).
 */
class TimeShiftOggTest {
    private long now = 1_000_000;

    private static byte[] opus() throws Exception {
        try (InputStream in = TimeShiftOggTest.class.getResourceAsStream("/timeshift-test.opus")) {
            assertNotNull(in);
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        }
    }

    /** Reads a reader until no more data is buffered right now. */
    private static byte[] drain(DataSource r, int max) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        while (o.size() < max) {
            int n = r.read(b, 0, Math.min(b.length, max - o.size()));
            if (n == C.RESULT_END_OF_INPUT) break;
            o.write(b, 0, n);
        }
        return o.toByteArray();
    }

    /** The bytes a reader returns must be a valid Ogg stream: headers, then whole pages. */
    private static void assertValidOgg(byte[] d) {
        assertTrue(d.length > 100);
        assertTrue(new String(d, 28, 8, java.nio.charset.StandardCharsets.ISO_8859_1).equals("OpusHead"),
                "stream must start with the OpusHead page");
        int p = 0, pages = 0;
        while (p < d.length) {
            assertTrue(OggPages.isPage(d, p), "page boundary expected at " + p);
            int pl = OggPages.pageLength(d, p, d.length);
            if (pl < 0) break; // last page cut by the read limit
            p += pl;
            pages++;
        }
        assertTrue(pages > 3);
    }

    @Test
    void oggKeepsHeadersAndStartsReadersOnPages() throws Exception {
        byte[] src = opus();
        TimeShiftBuffer ts = new TimeShiftBuffer(() -> null, Uri.EMPTY,
                new RingStore.Memory(512 * 1024), 60_000, 32, () -> now);
        DataSource r = ts.readerFactory().createDataSource();
        r.open(new DataSpec(Uri.EMPTY));
        int half = src.length / 2;
        for (int off = 0; off < half; off += 3000) {
            byte[] chunk = java.util.Arrays.copyOfRange(src, off, Math.min(half, off + 3000));
            ts.framer.push(chunk, chunk.length);
            now += 750;
        }
        assertTrue(ts.framer.ogg());
        assertNotNull(ts.framer.oggHeaders());
        assertValidOgg(drain(r, (int) ts.written() + ts.framer.oggHeaders().length));
        ts.onPlaybackStarted(0);

        // decoder-error recovery (+250 ms) and Back 5s must both give a decodable stream
        ts.recover(2000);
        DataSource r2 = ts.readerFactory().createDataSource();
        r2.open(new DataSpec(Uri.EMPTY));
        assertValidOgg(drain(r2, 20_000));

        ts.shift(1000, 5000);
        DataSource r3 = ts.readerFactory().createDataSource();
        r3.open(new DataSpec(Uri.EMPTY));
        assertValidOgg(drain(r3, 20_000));

        // a reconnect sends the header pages again: they must not end up in the ring
        long before = ts.written();
        ts.framer.newConnection();
        byte[] again = java.util.Arrays.copyOfRange(src, 0, 136);
        ts.framer.push(again, again.length);
        assertEquals(before, ts.written());
    }
}
