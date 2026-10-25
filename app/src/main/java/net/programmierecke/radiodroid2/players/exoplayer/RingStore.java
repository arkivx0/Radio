package net.programmierecke.radiodroid2.players.exoplayer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Radio Arkiv: fixed-size circular byte store for the time-shift buffer (RAM or cache file). */
public abstract class RingStore {
    protected final int capacity;

    protected RingStore(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    /** Write len bytes for absolute stream position absPos (wraps around). */
    public void write(long absPos, byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            int p = (int) (absPos % capacity);
            int n = Math.min(len, capacity - p);
            put(p, b, off, n);
            absPos += n; off += n; len -= n;
        }
    }

    /** Read len bytes from absolute stream position absPos (wraps around). */
    public void read(long absPos, byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            int p = (int) (absPos % capacity);
            int n = Math.min(len, capacity - p);
            get(p, b, off, n);
            absPos += n; off += n; len -= n;
        }
    }

    protected abstract void put(int pos, byte[] b, int off, int len) throws IOException;

    protected abstract void get(int pos, byte[] b, int off, int len) throws IOException;

    public void close() {
    }

    public static final class Memory extends RingStore {
        private final byte[] ring;

        public Memory(int capacity) {
            super(capacity);
            ring = new byte[capacity];
        }

        @Override
        protected void put(int pos, byte[] b, int off, int len) {
            System.arraycopy(b, off, ring, pos, len);
        }

        @Override
        protected void get(int pos, byte[] b, int off, int len) {
            System.arraycopy(ring, pos, b, off, len);
        }
    }

    /** Disk-backed ring in the app cache, for large buffers (e.g. 30 min at 320 kbps). */
    public static final class Disk extends RingStore {
        private final File file;
        private final RandomAccessFile raf;

        public Disk(File file, int capacity) throws IOException {
            super(capacity);
            this.file = file;
            this.raf = new RandomAccessFile(file, "rw");
            raf.setLength(capacity);
        }

        @Override
        protected synchronized void put(int pos, byte[] b, int off, int len) throws IOException {
            raf.seek(pos);
            raf.write(b, off, len);
        }

        @Override
        protected synchronized void get(int pos, byte[] b, int off, int len) throws IOException {
            raf.seek(pos);
            raf.readFully(b, off, len);
        }

        @Override
        public synchronized void close() {
            try { raf.close(); } catch (IOException ignored) { }
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
