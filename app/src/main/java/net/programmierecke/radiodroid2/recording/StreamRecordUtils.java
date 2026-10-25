package net.programmierecke.radiodroid2.recording;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Radio Arkiv: pure helpers for the silent (download-only) recorder. */
public final class StreamRecordUtils {
    private StreamRecordUtils() {
    }

    private static final Pattern URL_PATTERN = Pattern.compile("(?im)^\\s*(?:File\\d+\\s*=\\s*)?(https?://\\S+)");

    /** File extension for a stream's Content-Type (falls back to the URL, then mp3). */
    public static String extensionFor(String contentType, String url) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        if (ct.contains("aac") || ct.contains("mp4a")) return "aac";
        if (ct.contains("ogg") || ct.contains("opus") || ct.contains("vorbis")) return "ogg";
        if (ct.contains("flac")) return "flac";
        if (ct.contains("mpeg") || ct.contains("mp3")) return "mp3";
        String u = url == null ? "" : url.toLowerCase(Locale.US);
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        if (u.endsWith(".aac")) return "aac";
        if (u.endsWith(".ogg") || u.endsWith(".opus")) return "ogg";
        if (u.endsWith(".flac")) return "flac";
        return "mp3";
    }

    /** True when the response is a playlist (m3u/pls) rather than audio. */
    public static boolean isPlaylist(String contentType, String url) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        if (ct.contains("mpegurl") || ct.contains("scpls") || ct.contains("x-scpls")) return true;
        String u = url == null ? "" : url.toLowerCase(Locale.US);
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        return u.endsWith(".m3u") || u.endsWith(".pls");
    }

    /** True for HLS, which the silent recorder cannot capture. */
    public static boolean isHls(String contentType, String url) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        if (ct.contains("vnd.apple.mpegurl")) return true;
        String u = url == null ? "" : url.toLowerCase(Locale.US);
        return u.contains(".m3u8");
    }

    /** First http(s) URL in an m3u or pls body, or null. */
    public static String firstUrlInPlaylist(String body) {
        if (body == null) return null;
        Matcher m = URL_PATTERN.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    /** Milliseconds left until the end time, never negative. */
    public static long remainingMs(long endTimeMs, long nowMs) {
        return Math.max(0, endTimeMs - nowMs);
    }
}
