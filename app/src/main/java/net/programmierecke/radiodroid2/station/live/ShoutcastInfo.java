package net.programmierecke.radiodroid2.station.live;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.exoplayer2.metadata.icy.IcyHeaders;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Response;

import static net.programmierecke.radiodroid2.Utils.parseIntWithDefault;

public class ShoutcastInfo implements Parcelable {
    public int metadataOffset;
    public int bitrate;

    // e.g.: ice-audio-info: ice-samplerate=44100;ice-bitrate=128;ice-channels=2
    public String audioInfo;

    public String audioDesc;

    // e.g.: icy-genre:Pop / Rock
    public String audioGenre;

    public String audioName;
    public String audioHomePage;

    // e.g.: Server: Icecast 2.3.2
    public String serverName;

    public boolean serverPublic;
    public int channels;
    public int sampleRate;

    // Decoder-observed source format. These values describe the compressed
    // stream presented to the player, not Android's final hardware output.
    public String sampleMimeType;
    public String codecs;

    public ShoutcastInfo() {
    }

    public ShoutcastInfo(ShoutcastInfo other) {
        mergeFrom(other);
    }

    public ShoutcastInfo(IcyHeaders icyHeaders) {
        this.metadataOffset = Math.max(icyHeaders.metadataInterval, 0);
        this.bitrate = bitsPerSecondToKbps(icyHeaders.bitrate);
        this.audioGenre = icyHeaders.genre;
        this.serverPublic = icyHeaders.isPublic;
        this.audioName = icyHeaders.name;
        this.audioHomePage = icyHeaders.url;
    }

    public static ShoutcastInfo Decode(Response response) {
        ShoutcastInfo info = new ShoutcastInfo();
        info.metadataOffset = parseIntWithDefault(response.header("icy-metaint"), 0);
        info.bitrate = parseIntWithDefault(response.header("icy-br"), 0);
        // e.g.: ice-audio-info: ice-samplerate=44100;ice-bitrate=128;ice-channels=2
        info.audioInfo = response.header("ice-audio-info");
        info.audioDesc = response.header("icy-description");
        // e.g.: icy-genre:Pop / Rock
        info.audioGenre = response.header("icy-genre");
        info.audioName = response.header("icy-name");
        info.audioHomePage = response.header("icy-url");
        // e.g.: Server: Icecast 2.3.2
        info.serverName = response.header("Server");
        info.serverPublic = parseIntWithDefault(response.header("icy-pub"), 0) > 0;

        if (info.audioInfo != null) {
            Map<String, String> audioInfoParams = splitAudioInfo(info.audioInfo);

            info.channels = parseIntWithDefault(audioInfoParams.get("ice-channels"), 0);
            if (info.channels == 0) {
                info.channels = parseIntWithDefault(audioInfoParams.get("channels"), 0);
            }

            info.sampleRate = parseIntWithDefault(audioInfoParams.get("ice-samplerate"), 0);
            if (info.sampleRate == 0) {
                info.sampleRate = parseIntWithDefault(audioInfoParams.get("samplerate"), 0);
            }

            if (info.bitrate == 0) {
                info.bitrate = parseIntWithDefault(audioInfoParams.get("ice-bitrate"), 0);
                if (info.bitrate == 0) {
                    info.bitrate = parseIntWithDefault(audioInfoParams.get("bitrate"), 0);
                }
            }
        }

        return info.hasUsefulInfo() ? info : null;
    }

    private static Map<String, String> splitAudioInfo(String audioInfo) {
        Map<String, String> params = new LinkedHashMap<>();
        String[] pairs = audioInfo.split(";");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx <= 0) {
                continue;
            }

            String key = pair.substring(0, idx).trim().toLowerCase(Locale.US);
            if (key.isEmpty()) {
                continue;
            }

            params.put(key, pair.substring(idx + 1).trim());
        }
        return params;
    }

    public boolean hasMetadata() {
        return metadataOffset > 0;
    }

    public boolean hasUsefulInfo() {
        return hasMetadata()
                || bitrate > 0
                || channels > 0
                || sampleRate > 0
                || isNonEmpty(audioInfo)
                || isNonEmpty(audioDesc)
                || isNonEmpty(audioGenre)
                || isNonEmpty(audioName)
                || isNonEmpty(audioHomePage)
                || isNonEmpty(serverName)
                || isNonEmpty(sampleMimeType)
                || isNonEmpty(codecs);
    }

    public void mergeFrom(ShoutcastInfo other) {
        if (other == null) {
            return;
        }

        if (other.metadataOffset > 0) metadataOffset = other.metadataOffset;
        if (other.bitrate > 0) bitrate = other.bitrate;
        if (isNonEmpty(other.audioInfo)) audioInfo = other.audioInfo;
        if (isNonEmpty(other.audioDesc)) audioDesc = other.audioDesc;
        if (isNonEmpty(other.audioGenre)) audioGenre = other.audioGenre;
        if (isNonEmpty(other.audioName)) audioName = other.audioName;
        if (isNonEmpty(other.audioHomePage)) audioHomePage = other.audioHomePage;
        if (isNonEmpty(other.serverName)) serverName = other.serverName;
        serverPublic = serverPublic || other.serverPublic;
        if (other.channels > 0) channels = other.channels;
        if (other.sampleRate > 0) sampleRate = other.sampleRate;
        if (isNonEmpty(other.sampleMimeType)) sampleMimeType = other.sampleMimeType;
        if (isNonEmpty(other.codecs)) codecs = other.codecs;
    }

    public void mergeObservedAudioFormat(String sampleMimeType, String codecs, int sampleRate,
                                         int channels, int bitrateBitsPerSecond) {
        if (isNonEmpty(sampleMimeType)) this.sampleMimeType = sampleMimeType;
        if (isNonEmpty(codecs)) this.codecs = codecs;
        if (sampleRate > 0) this.sampleRate = sampleRate;
        if (channels > 0) this.channels = channels;
        if (bitrate <= 0) bitrate = bitsPerSecondToKbps(bitrateBitsPerSecond);
    }

    public String getCodecDisplayName() {
        if (isNonEmpty(codecs)) {
            String normalizedCodecs = codecs.trim().toLowerCase(Locale.US);
            if (normalizedCodecs.startsWith("mp4a.40.29")) return "HE-AAC v2";
            if (normalizedCodecs.startsWith("mp4a.40.5")) return "HE-AAC";
            if (normalizedCodecs.startsWith("mp4a.40.2")) return "AAC-LC";
            if (normalizedCodecs.startsWith("opus")) return "Opus";
            if (normalizedCodecs.startsWith("vorbis")) return "Vorbis";
            if (normalizedCodecs.startsWith("flac")) return "FLAC";
            if (normalizedCodecs.startsWith("mp3")) return "MP3";
        }

        if (isNonEmpty(sampleMimeType)) {
            String normalizedMimeType = sampleMimeType.trim().toLowerCase(Locale.US);
            switch (normalizedMimeType) {
                case "audio/mp4a-latm":
                case "audio/aac":
                    return "AAC";
                case "audio/mpeg":
                    return "MP3";
                case "audio/mpeg-l1":
                    return "MP1";
                case "audio/mpeg-l2":
                    return "MP2";
                case "audio/opus":
                    return "Opus";
                case "audio/vorbis":
                    return "Vorbis";
                case "audio/flac":
                    return "FLAC";
                case "audio/ac3":
                    return "AC-3";
                case "audio/eac3":
                    return "E-AC-3";
                default:
                    return sampleMimeType;
            }
        }

        return isNonEmpty(codecs) ? codecs : null;
    }

    private static int bitsPerSecondToKbps(int bitsPerSecond) {
        return bitsPerSecond > 0 ? Math.round(bitsPerSecond / 1000f) : 0;
    }

    private static boolean isNonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    protected ShoutcastInfo(Parcel in) {
        metadataOffset = in.readInt();
        bitrate = in.readInt();
        audioInfo = in.readString();
        audioDesc = in.readString();
        audioGenre = in.readString();
        audioName = in.readString();
        audioHomePage = in.readString();
        serverName = in.readString();
        serverPublic = in.readByte() != 0;
        channels = in.readInt();
        sampleRate = in.readInt();
        sampleMimeType = in.readString();
        codecs = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(metadataOffset);
        dest.writeInt(bitrate);
        dest.writeString(audioInfo);
        dest.writeString(audioDesc);
        dest.writeString(audioGenre);
        dest.writeString(audioName);
        dest.writeString(audioHomePage);
        dest.writeString(serverName);
        dest.writeByte((byte) (serverPublic ? 1 : 0));
        dest.writeInt(channels);
        dest.writeInt(sampleRate);
        dest.writeString(sampleMimeType);
        dest.writeString(codecs);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ShoutcastInfo> CREATOR = new Creator<ShoutcastInfo>() {
        @Override
        public ShoutcastInfo createFromParcel(Parcel in) {
            return new ShoutcastInfo(in);
        }

        @Override
        public ShoutcastInfo[] newArray(int size) {
            return new ShoutcastInfo[size];
        }
    };
}
