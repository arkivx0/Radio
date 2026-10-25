package net.programmierecke.radiodroid2.recording;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamRecordUtilsTest {
    @Test
    void extensionFromContentType() {
        assertEquals("mp3", StreamRecordUtils.extensionFor("audio/mpeg", null));
        assertEquals("aac", StreamRecordUtils.extensionFor("audio/aacp", null));
        assertEquals("aac", StreamRecordUtils.extensionFor("audio/aac; charset=x", null));
        assertEquals("ogg", StreamRecordUtils.extensionFor("application/ogg", null));
        assertEquals("ogg", StreamRecordUtils.extensionFor("audio/ogg; codecs=opus", null));
        assertEquals("flac", StreamRecordUtils.extensionFor("audio/flac", null));
    }

    @Test
    void extensionFallsBackToUrlThenMp3() {
        assertEquals("aac", StreamRecordUtils.extensionFor(null, "http://x/stream.aac?x=1"));
        assertEquals("ogg", StreamRecordUtils.extensionFor("", "http://x/live.opus"));
        assertEquals("mp3", StreamRecordUtils.extensionFor("application/octet-stream", "http://x/live"));
    }

    @Test
    void playlistAndHlsDetection() {
        assertTrue(StreamRecordUtils.isPlaylist("audio/x-mpegurl", "http://x/a"));
        assertTrue(StreamRecordUtils.isPlaylist(null, "http://x/listen.pls"));
        assertFalse(StreamRecordUtils.isPlaylist("audio/mpeg", "http://x/stream"));
        assertTrue(StreamRecordUtils.isHls("application/vnd.apple.mpegurl", "http://x/a"));
        assertTrue(StreamRecordUtils.isHls(null, "http://x/master.m3u8"));
        assertFalse(StreamRecordUtils.isHls("audio/mpeg", "http://x/stream.mp3"));
    }

    @Test
    void firstUrlInPlaylists() {
        assertEquals("http://a.example/live", StreamRecordUtils.firstUrlInPlaylist("#EXTM3U\n#EXTINF:-1,x\nhttp://a.example/live\n"));
        assertEquals("https://b.example/s.mp3", StreamRecordUtils.firstUrlInPlaylist("[playlist]\nNumberOfEntries=1\nFile1=https://b.example/s.mp3\nTitle1=x\n"));
        assertNull(StreamRecordUtils.firstUrlInPlaylist("[playlist]\nNumberOfEntries=0\n"));
        assertNull(StreamRecordUtils.firstUrlInPlaylist(null));
    }

    @Test
    void remainingTimeNeverNegative() {
        assertEquals(5000, StreamRecordUtils.remainingMs(10_000, 5_000));
        assertEquals(0, StreamRecordUtils.remainingMs(10_000, 20_000));
    }
}
