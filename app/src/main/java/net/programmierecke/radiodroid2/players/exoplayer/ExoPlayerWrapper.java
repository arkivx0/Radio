package net.programmierecke.radiodroid2.players.exoplayer;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.icy.IcyHeaders;
import com.google.android.exoplayer2.metadata.icy.IcyInfo;
import com.google.android.exoplayer2.metadata.id3.Id3Frame;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.HttpDataSource;

import net.programmierecke.radiodroid2.BuildConfig;
import net.programmierecke.radiodroid2.R;
import net.programmierecke.radiodroid2.Utils;
import net.programmierecke.radiodroid2.players.PlayState;
import net.programmierecke.radiodroid2.players.PlayerWrapper;
import net.programmierecke.radiodroid2.recording.RecordableListener;
import net.programmierecke.radiodroid2.station.live.ShoutcastInfo;
import net.programmierecke.radiodroid2.station.live.StreamLiveInfo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.OkHttpClient;

public class ExoPlayerWrapper implements PlayerWrapper, IcyDataSource.IcyDataSourceListener, Player.Listener {

    final private String TAG = "ExoPlayerWrapper";

    private ExoPlayer player;
    private PlayListener stateListener;

    private String streamUrl;

    private final DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

    private RecordableListener recordableListener;

    private long totalTransferredBytes;
    private long currentPlaybackTransferredBytes;

    private boolean isHls;
    private boolean isPlayingFlag;

    private Handler playerThreadHandler;

    private Context context;
    private MediaSource audioSource;

    private Runnable fullStopTask;

    private final Object streamInfoLock = new Object();
    private ShoutcastInfo streamAudioInfo;

    private final BroadcastReceiver networkChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!localFile && fullStopTask != null && player != null && audioSource != null && Utils.hasAnyConnection(context)) {
                Log.i(TAG, "Regained connection. Resuming playback.");

                cancelStopTask();

                player.setMediaSource(audioSource);
                player.prepare();
                player.setPlayWhenReady(true);
            }
        }
    };

    @Override
    public void playRemote(@NonNull OkHttpClient httpClient, @NonNull String streamUrl, @NonNull Context context, boolean isAlarm) {
        // I don't know why, but it is still possible that streamUrl is null,
        // I still get exceptions from this from google
        if (!streamUrl.equals(this.streamUrl)) {
            currentPlaybackTransferredBytes = 0;
        }

        this.context = context;
        this.streamUrl = streamUrl;
        isHls = Utils.urlIndicatesHlsStream(streamUrl);

        cancelStopTask();

        stateListener.onStateChanged(PlayState.PrePlaying);

        if (player != null) {
            player.stop();
        }

        synchronized (streamInfoLock) {
            streamAudioInfo = null;
        }
        stateListener.onDataSourceShoutcastInfo(null, isHls);

        if (player == null) {
            player = new ExoPlayer.Builder(context).build();
            player.setAudioAttributes(new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(isAlarm ? C.USAGE_ALARM : C.USAGE_MEDIA).build(), false);

            player.addListener(this);
            player.addAnalyticsListener(new AnalyticEventListener());
        }

        if (playerThreadHandler == null) {
            playerThreadHandler = new Handler(Looper.getMainLooper());
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        final int retryTimeout = prefs.getInt("settings_retry_timeout", 10);
        final int retryDelay = prefs.getInt("settings_retry_delay", 100);

        DataSource.Factory dataSourceFactory = new RadioDataSourceFactory(httpClient, bandwidthMeter, this, retryTimeout, retryDelay);
        // Produces Extractor instances for parsing the media data.
        stopTimeShift();
        localFile = net.programmierecke.radiodroid2.station.DataRadioStation.isLocalFileUrl(streamUrl);
        if (localFile) {
            // Radio fork: local file (recording or file opened with Radio) - normal seekable playback
            isHls = false;
            if (!streamUrl.equals(localResumeUrl)) {
                localResumeMs = 0;
            }
            localResumeUrl = streamUrl;
            audioSource = new ProgressiveMediaSource.Factory(
                    new com.google.android.exoplayer2.upstream.DefaultDataSource.Factory(context))
                    .createMediaSource(MediaItem.fromUri(Uri.parse(streamUrl)));
            player.setMediaSource(audioSource);
            player.prepare();
            if (localResumeMs > 0) {
                player.seekTo(localResumeMs);
            }
        } else if (!isHls) {
            // Radio fork: route progressive streams through the time-shift ring buffer
            timeShift = createTimeShift(dataSourceFactory, Uri.parse(streamUrl), prefs);
            timeShift.start();
            shiftMs = 0;
            audioSource = new ProgressiveMediaSource.Factory(timeShift.readerFactory())
                    .setLoadErrorHandlingPolicy(new CustomLoadErrorHandlingPolicy())
                    .createMediaSource(MediaItem.fromUri(Uri.parse(streamUrl)));
            player.setMediaSource(audioSource);
            player.prepare();
        } else {
            audioSource = new HlsMediaSource.Factory(dataSourceFactory)
                    .setLoadErrorHandlingPolicy(new CustomLoadErrorHandlingPolicy())
                    .createMediaSource(MediaItem.fromUri(Uri.parse(streamUrl)));
            player.setMediaSource(audioSource);
            player.prepare();
        }

        player.setPlayWhenReady(true);

        context.registerReceiver(networkChangedReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        // State changed will be called when audio session id is available.
    }

    private TimeShiftBuffer timeShift;
    private long shiftMs;
    private int expectedBitrateKbps;

    /** Station's declared bitrate, used to size the skip-back buffer in minutes. */
    public void setExpectedBitrateKbps(int kbps) {
        expectedBitrateKbps = kbps;
    }

    private TimeShiftBuffer createTimeShift(DataSource.Factory upstream, Uri uri, SharedPreferences prefs) {
        String pref = prefs.getString(TimeShiftSettings.PREF_KEY, TimeShiftSettings.DEFAULT);
        int kbps = expectedBitrateKbps;
        int capacity = TimeShiftSettings.capacityFor(pref, kbps);
        long maxBehind = TimeShiftSettings.maxBehindMsFor(pref, kbps);
        RingStore store = null;
        if (TimeShiftSettings.useDisk(capacity)) {
            try {
                store = new RingStore.Disk(new java.io.File(context.getCacheDir(), "timeshift.buf"), capacity);
            } catch (java.io.IOException e) {
                Log.w(TAG, "disk time-shift buffer unavailable, using RAM", e);
            }
        }
        if (store == null) {
            int ramCap = Math.min(capacity, TimeShiftSettings.MAX_RAM_BYTES);
            store = new RingStore.Memory(ramCap);
            maxBehind = Math.min(maxBehind, (ramCap - 256 * 1024) * 8000L / (TimeShiftSettings.effectiveKbps(kbps) * 1000L));
        }
        return new TimeShiftBuffer(upstream, uri, store, maxBehind, kbps);
    }

    // Radio fork: local file playback state
    private boolean localFile;
    private String localResumeUrl;
    private long localResumeMs;

    public boolean isLocalFile() {
        return localFile;
    }

    public long getPositionMs() {
        return player != null ? Math.max(0, player.getCurrentPosition()) : (localFile ? localResumeMs : 0);
    }

    public long getDurationMs() {
        if (player == null) return -1;
        long d = player.getDuration();
        return d == C.TIME_UNSET ? -1 : d;
    }

    /** Seek within a local file. Must run on player thread. */
    public void seekTo(long positionMs) {
        if (!localFile) return;
        positionMs = Math.max(0, positionMs);
        long d = getDurationMs();
        if (d > 0) positionMs = Math.min(positionMs, d);
        if (player != null) {
            player.seekTo(positionMs);
        } else {
            localResumeMs = positionMs;
        }
    }

    private void stopTimeShift() {
        if (timeShift != null) {
            timeShift.stop();
            timeShift = null;
        }
        shiftMs = 0;
    }

    public boolean canTimeShift() {
        return timeShift != null && player != null;
    }

    /** Move playback deltaMs further behind live (negative = towards live). Must run on player thread. */
    public void shiftBy(long deltaMs) {
        if (localFile) {
            // "Back 5s" on a local file is a plain relative seek; "Go live" means nothing here.
            if (deltaMs != Long.MIN_VALUE) seekTo(getPositionMs() - deltaMs);
            return;
        }
        if (!canTimeShift()) return;
        // Measure from what is actually heard: reader cursor behind live plus the player's own
        // read-ahead. Using only the last requested shift made Back 5s land near the live edge.
        long heardBehind = timeShift.readerBehindMs() + Math.max(0, getBufferedMs());
        long target = deltaMs == Long.MIN_VALUE ? 0 : Math.max(0, heardBehind + deltaMs);
        target = Math.min(target, timeShift.maxBehindMs());
        shiftMs = timeShift.seekBehind(target);
        if (shiftMs < 1000) shiftMs = 0;
        player.stop();
        player.setMediaSource(audioSource);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    public long getBehindLiveMs() {
        return canTimeShift() ? shiftMs : 0;
    }

    @Override
    public void pause() {
        Log.i(TAG, "Pause. Stopping exoplayer.");
        stopTimeShift();
        if (localFile && player != null) {
            localResumeMs = Math.max(0, player.getCurrentPosition());
        }

        cancelStopTask();

        if (player != null) {
            context.unregisterReceiver(networkChangedReceiver);
            player.stop();
            player.release();
            player = null;
        }
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stopping exoplayer.");
        stopTimeShift();
        localResumeMs = 0;
        localResumeUrl = null;

        cancelStopTask();

        if (player != null) {
            context.unregisterReceiver(networkChangedReceiver);
            player.stop();
            player.release();
            player = null;
        }

        stopRecording();
    }

    @Override
    public boolean isPlaying() {
        return player != null && isPlayingFlag;
    }

    @Override
    public long getBufferedMs() {
        if (player != null) {
            return (int) (player.getBufferedPosition() - player.getCurrentPosition());
        }

        return 0;
    }

    @Override
    public int getAudioSessionId() {
        if (player != null) {
            return player.getAudioSessionId();
        }
        return 0;
    }

    @Override
    public long getTotalTransferredBytes() {
        return totalTransferredBytes;
    }

    @Override
    public long getCurrentPlaybackTransferredBytes() {
        return currentPlaybackTransferredBytes;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public void setVolume(float newVolume) {
        if (player != null) {
            player.setVolume(newVolume);
        }
    }

    @Override
    public void setStateListener(PlayListener listener) {
        stateListener = listener;
    }

    @Override
    public void onDataSourceConnected() {

    }

    @Override
    public void onDataSourceConnectionLost() {

    }

    @Override
    public void onMetadata(@NonNull Metadata metadata) {
        if (BuildConfig.DEBUG) Log.d(TAG, "META: " + metadata);
        final int length = metadata.length();
        if (length > 0) {
            for (int i = 0; i < length; i++) {
                final Metadata.Entry entry = metadata.get(i);
                if (entry == null) {
                    continue;
                }
                if (entry instanceof IcyInfo) {
                    final IcyInfo icyInfo = ((IcyInfo) entry);
                    Log.d(TAG, "IcyInfo: " + icyInfo);
                    if (icyInfo.title != null) {
                        Map<String, String> rawMetadata = new HashMap<>() {{
                            put("StreamTitle", icyInfo.title);
                        }};
                        StreamLiveInfo streamLiveInfo = new StreamLiveInfo(rawMetadata);
                        onDataSourceStreamLiveInfo(streamLiveInfo);
                    }
                } else if (entry instanceof IcyHeaders) {
                    final IcyHeaders icyHeaders = ((IcyHeaders) entry);
                    Log.d(TAG, "IcyHeaders: " + icyHeaders);
                    onDataSourceShoutcastInfo(new ShoutcastInfo(icyHeaders));
                } else if (entry instanceof Id3Frame) {
                    final Id3Frame id3Frame = ((Id3Frame) entry);
                    Log.d(TAG, "id3 metadata: " + id3Frame);
                }
            }
        }
    }

    @Override
    public void onDataSourceConnectionLostIrrecoverably() {
        Log.i(TAG, "Connection lost irrecoverably.");
    }

    void resumeWhenNetworkConnected() {
        playerThreadHandler.post(() -> {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            int resumeWithin = sharedPref.getInt("settings_resume_within", 60);
            if (resumeWithin > 0) {
                Log.d(TAG, "Trying to resume playback within " + resumeWithin + "s.");

                // We want user to be able to paused during connection loss.
                // TODO: Find a way to notify user that even if current state is Playing
                //       we are actually trying to reconnect.
                //stateListener.onStateChanged(PlayState.Paused);

                cancelStopTask();

                fullStopTask = () -> {
                    stop();
                    stateListener.onPlayerError(R.string.giving_up_resume);

                    ExoPlayerWrapper.this.fullStopTask = null;
                };
                playerThreadHandler.postDelayed(fullStopTask, resumeWithin * 1000L);

                stateListener.onPlayerWarning(R.string.warning_no_network_trying_resume);
            } else {
                stop();

                stateListener.onPlayerError(R.string.error_stream_reconnect_timeout);
            }
        });
    }

    @Override
    public void onDataSourceShoutcastInfo(@Nullable ShoutcastInfo shoutcastInfo) {
        ShoutcastInfo publishedInfo;
        synchronized (streamInfoLock) {
            if (shoutcastInfo != null) {
                if (streamAudioInfo == null) {
                    streamAudioInfo = new ShoutcastInfo();
                }
                streamAudioInfo.mergeFrom(shoutcastInfo);
            }
            publishedInfo = streamAudioInfo != null && streamAudioInfo.hasUsefulInfo()
                    ? new ShoutcastInfo(streamAudioInfo)
                    : null;
        }
        stateListener.onDataSourceShoutcastInfo(publishedInfo, isHls);
    }

    private void onObservedAudioFormat(@NonNull Format format) {
        int bitrateBitsPerSecond = format.averageBitrate > 0
                ? format.averageBitrate
                : format.bitrate;

        ShoutcastInfo publishedInfo;
        synchronized (streamInfoLock) {
            if (streamAudioInfo == null) {
                streamAudioInfo = new ShoutcastInfo();
            }
            streamAudioInfo.mergeObservedAudioFormat(
                    format.sampleMimeType,
                    format.codecs,
                    format.sampleRate,
                    format.channelCount,
                    bitrateBitsPerSecond);
            publishedInfo = new ShoutcastInfo(streamAudioInfo);
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Source audio format: mime=" + format.sampleMimeType
                    + ", codecs=" + format.codecs
                    + ", sampleRate=" + format.sampleRate
                    + ", channels=" + format.channelCount
                    + ", averageBitrate=" + format.averageBitrate);
        }
        stateListener.onDataSourceShoutcastInfo(publishedInfo, isHls);
    }

    @Override
    public void onDataSourceStreamLiveInfo(StreamLiveInfo streamLiveInfo) {
        stateListener.onDataSourceStreamLiveInfo(streamLiveInfo);
    }

    @Override
    public void onDataSourceBytesRead(byte[] buffer, int offset, int length) {
        totalTransferredBytes += length;
        currentPlaybackTransferredBytes += length;

        if (recordableListener != null) {
            recordableListener.onBytesAvailable(buffer, offset, length);
        }
    }

    @Override
    public boolean canRecord() {
        return player != null && !localFile;
    }

    @Override
    public void startRecording(@NonNull RecordableListener recordableListener) {
        this.recordableListener = recordableListener;
    }

    @Override
    public void stopRecording() {
        if (recordableListener != null) {
            recordableListener.onRecordingEnded();
            recordableListener = null;
        }
    }

    @Override
    public boolean isRecording() {
        return recordableListener != null;
    }

    @Override
    public Map<String, String> getRecordNameFormattingArgs() {
        return null;
    }

    @Override
    public String getExtension() {
        return isHls ? "ts" : "mp3";
    }

    private void cancelStopTask() {
        if (fullStopTask != null) {
            playerThreadHandler.removeCallbacks(fullStopTask);
            fullStopTask = null;
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        // Do nothing
    }

    @Override
    public void onPlayerErrorChanged(PlaybackException error) {
        Log.d(TAG, "Player error: ", error);
        // Stop playing since it is either irrecoverable error in the player or our data source failed to reconnect.
        if (fullStopTask != null) {
            stop();
            stateListener.onPlayerError(R.string.error_play_stream);
        }
    }

    @Override
    public void onPlaybackParametersChanged(@NonNull PlaybackParameters playbackParameters) {
        // Do nothing
    }

    final class CustomLoadErrorHandlingPolicy extends DefaultLoadErrorHandlingPolicy {
        final int MIN_RETRY_DELAY_MS = 10;
        final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        // We need to read the retry delay here on each error again because the user might change
        // this value between retries and experiment with different vales to get the best result for
        // the specific situation. We also need to make sure that a sensible minimum value is chosen.
        int getSanitizedRetryDelaySettingsMs() {
            return Math.max(sharedPrefs.getInt("settings_retry_delay", 100), MIN_RETRY_DELAY_MS);
        }

        @Override
        public long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {

            int retryDelay = getSanitizedRetryDelaySettingsMs();
            IOException exception = loadErrorInfo.exception;

            if (exception instanceof HttpDataSource.InvalidContentTypeException) {
                stateListener.onPlayerError(R.string.error_play_stream);
                return C.TIME_UNSET; // Immediately surface error if we cannot play content type
            }

            if (!Utils.hasAnyConnection(context)) {
                int resumeWithinS = sharedPrefs.getInt("settings_resume_within", 60);
                if (resumeWithinS > 0) {
                    resumeWhenNetworkConnected();
                    retryDelay = 1000 * resumeWithinS + retryDelay;
                }
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Providing retry delay of " + retryDelay + "ms " +
                        "error count: " + loadErrorInfo.errorCount + ", " +
                        "exception " + exception.getClass() + ", " +
                        "message: " + exception.getMessage());
            }
            return retryDelay;
        }

        @Override
        public int getMinimumLoadableRetryCount(int dataType) {
            return sharedPrefs.getInt("settings_retry_timeout", 10) * 1000 / getSanitizedRetryDelaySettingsMs() + 1;
        }
    }

    private class AnalyticEventListener implements AnalyticsListener {
        @Override
        public void onAudioInputFormatChanged(@NonNull EventTime eventTime, @NonNull Format format,
                                              @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
            onObservedAudioFormat(format);
        }

        @Override
        public void onPlayerStateChanged(EventTime eventTime, boolean playWhenReady, int playbackState) {
            isPlayingFlag = playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING;

            switch (playbackState) {
                case Player.STATE_READY:
                    cancelStopTask();
                    stateListener.onStateChanged(PlayState.Playing);
                    break;
                case Player.STATE_BUFFERING:
                    stateListener.onStateChanged(PlayState.PrePlaying);
                    break;
            }

        }

        @Override
        public void onTimelineChanged(@NonNull EventTime eventTime, int reason) {

        }

        @Override
        public void onPlaybackParametersChanged(@NonNull EventTime eventTime, @NonNull PlaybackParameters playbackParameters) {

        }

        @Override
        public void onRepeatModeChanged(@NonNull EventTime eventTime, int repeatMode) {

        }

        @Override
        public void onShuffleModeChanged(@NonNull EventTime eventTime, boolean shuffleModeEnabled) {

        }

        @Override
        public void onBandwidthEstimate(@NonNull EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {

        }

        @Override
        public void onSurfaceSizeChanged(@NonNull EventTime eventTime, int width, int height) {

        }

        @Override
        public void onMetadata(@NonNull EventTime eventTime, @NonNull Metadata metadata) {

        }

        @Override
        public void onAudioAttributesChanged(@NonNull EventTime eventTime, @NonNull AudioAttributes audioAttributes) {

        }

        @Override
        public void onVolumeChanged(@NonNull EventTime eventTime, float volume) {

        }

        @Override
        public void onDroppedVideoFrames(@NonNull EventTime eventTime, int droppedFrames, long elapsedMs) {

        }

        @Override
        public void onDrmKeysLoaded(@NonNull EventTime eventTime) {

        }

        @Override
        public void onDrmSessionManagerError(@NonNull EventTime eventTime, @NonNull Exception error) {

        }

        @Override
        public void onDrmKeysRestored(@NonNull EventTime eventTime) {

        }

        @Override
        public void onDrmKeysRemoved(@NonNull EventTime eventTime) {

        }

        @Override
        public void onDrmSessionReleased(@NonNull EventTime eventTime) {

        }
    }
}
