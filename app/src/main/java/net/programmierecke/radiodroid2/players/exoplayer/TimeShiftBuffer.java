package net.programmierecke.radiodroid2.players.exoplayer;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;

/**
 * Radio fork: live time-shift. A feeder thread reads the network stream (through the normal
 * IcyDataSource, so metadata + recording keep working) into a ring buffer (~5 min). The player
 * reads from the ring at a cursor, so it can be restarted N seconds behind the live edge.
 */
public class TimeShiftBuffer {
    private static final String TAG = "TimeShift";
    private static final int MARGIN = 64 * 1024;

    private final RingStore ring;
    private final int capacity;
    private final long maxBehindMs;
    private final double knownBytesPerSecond;
    private long written;          // total bytes ever written
    private long firstByteTime;    // ms when first byte arrived
    private volatile boolean stopped;
    private volatile IOException error;
    // Start position used by every reader open until the next seek (ExoPlayer may open more than once).
    private long startPos = -1;
    private volatile long readerPos;    // cursor of current reader
    private final DataSource.Factory upstreamFactory;
    private final Uri uri;
    private Thread feeder;

    public TimeShiftBuffer(@NonNull DataSource.Factory upstreamFactory, @NonNull Uri uri,
                           @NonNull RingStore ring, long maxBehindMs, int bitrateKbps) {
        this.upstreamFactory = upstreamFactory;
        this.uri = uri;
        this.ring = ring;
        this.capacity = ring.capacity();
        this.maxBehindMs = maxBehindMs;
        this.knownBytesPerSecond = bitrateKbps > 0 ? bitrateKbps * 1000.0 / 8.0 : 0;
    }

    public void start() {
        feeder = new Thread(this::feed, "TimeShiftFeeder");
        feeder.setDaemon(true);
        feeder.start();
    }

    public void stop() {
        stopped = true;
        if (feeder != null) feeder.interrupt();
        synchronized (this) { notifyAll(); }
        ring.close();
    }

    private void feed() {
        byte[] buf = new byte[16 * 1024];
        int failures = 0;
        while (!stopped) {
            DataSource src = upstreamFactory.createDataSource();
            try {
                src.open(new DataSpec(uri));
                failures = 0;
                while (!stopped) {
                    int n = src.read(buf, 0, buf.length);
                    if (n == C.RESULT_END_OF_INPUT) break;
                    if (n > 0) write(buf, n);
                }
            } catch (IOException e) {
                if (stopped) return;
                Log.w(TAG, "feeder error", e);
                if (++failures > 5) {
                    error = e;
                    synchronized (this) { notifyAll(); }
                    return;
                }
            } finally {
                try { src.close(); } catch (Exception ignored) { }
            }
            if (!stopped) {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { }
            }
        }
    }

    private synchronized void write(byte[] b, int n) throws IOException {
        if (stopped) return;
        if (written == 0) firstByteTime = System.currentTimeMillis();
        ring.write(written, b, 0, n);
        written += n;
        notifyAll();
    }

    /** Stream bytes per second: the stream's declared bitrate, else measured, else 128 kbit/s. */
    public synchronized double bytesPerSecond() {
        if (knownBytesPerSecond > 0) return knownBytesPerSecond;
        long el = System.currentTimeMillis() - firstByteTime;
        // ignore initial burst: needs at least 20 s of data
        if (written > 0 && el > 20000) return Math.max(2000, written * 1000.0 / el);
        return 16000;
    }

    private synchronized long oldest() {
        return Math.max(0, written - capacity + MARGIN);
    }

    /** Set where the next reader starts: msBehind = 0 is live. Returns actual ms behind. */
    public synchronized long seekBehind(long msBehind) {
        msBehind = Math.max(0, Math.min(msBehind, maxBehindMs));
        long pos = written - (long) (msBehind * bytesPerSecond() / 1000.0);
        pos = Math.max(pos, oldest());
        startPos = msBehind == 0 ? -1 : pos;
        return (long) ((written - pos) * 1000.0 / bytesPerSecond());
    }

    /** ms between the reader cursor and the live edge (excludes player's own buffer). */
    public synchronized long readerBehindMs() {
        return (long) (Math.max(0, written - readerPos) * 1000.0 / bytesPerSecond());
    }

    public synchronized long maxBehindMs() {
        return Math.min(maxBehindMs, (long) ((written - oldest()) * 1000.0 / bytesPerSecond()));
    }

    public DataSource.Factory readerFactory() {
        return Reader::new;
    }

    private class Reader implements DataSource {
        private long pos;
        private Uri openUri;

        @Override
        public void addTransferListener(@NonNull TransferListener transferListener) { }

        @Override
        public long open(@NonNull DataSpec dataSpec) throws IOException {
            openUri = dataSpec.uri;
            synchronized (TimeShiftBuffer.this) {
                long base = startPos >= 0 ? startPos : written;
                if (startPos < 0) startPos = base; // reopen of the same prepare keeps its start
                pos = Math.max(oldest(), base + Math.max(0, dataSpec.position));
                readerPos = pos;
            }
            return C.LENGTH_UNSET;
        }

        @Override
        public int read(@NonNull byte[] target, int offset, int length) throws IOException {
            if (length == 0) return 0;
            synchronized (TimeShiftBuffer.this) {
                long deadline = System.currentTimeMillis() + 20000;
                while (written <= pos) {
                    if (error != null) throw error;
                    if (stopped) return C.RESULT_END_OF_INPUT;
                    long wait = deadline - System.currentTimeMillis();
                    if (wait <= 0) throw new IOException("time-shift: no data from stream");
                    try {
                        TimeShiftBuffer.this.wait(Math.min(wait, 500));
                    } catch (InterruptedException e) {
                        throw new java.io.InterruptedIOException();
                    }
                }
                if (pos < oldest()) pos = oldest(); // overrun: jump forward
                int avail = (int) Math.min(length, written - pos);
                ring.read(pos, target, offset, avail);
                pos += avail;
                readerPos = pos;
                return avail;
            }
        }

        @Nullable
        @Override
        public Uri getUri() { return openUri; }

        @Override
        public void close() { }
    }
}
