package net.programmierecke.radiodroid2.station.live;

import com.google.android.exoplayer2.metadata.icy.IcyHeaders;

import org.junit.jupiter.api.Test;

import okhttp3.Headers;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoutcastInfoTest {

    @Test
    void decodePreservesUsefulHeadersWithoutMetadataInterval() {
        Response response = response(new Headers.Builder()
                .add("icy-br", "320")
                .add("ice-audio-info",
                        " ICE-SAMPLERATE = 44100 ; malformed ; ICE-CHANNELS=2 ; =ignored ")
                .add("icy-name", "Example Radio")
                .build());

        ShoutcastInfo info = ShoutcastInfo.Decode(response);

        assertNotNull(info);
        assertFalse(info.hasMetadata());
        assertEquals(320, info.bitrate);
        assertEquals(44100, info.sampleRate);
        assertEquals(2, info.channels);
        assertEquals("Example Radio", info.audioName);
    }

    @Test
    void decodeReturnsNullWhenNoUsefulStreamHeadersExist() {
        Response response = response(new Headers.Builder()
                .add("Content-Type", "audio/mpeg")
                .build());

        assertNull(ShoutcastInfo.Decode(response));
    }

    @Test
    void icyHeadersConvertBitsPerSecondToKbps() {
        IcyHeaders icyHeaders = new IcyHeaders(
                320000,
                "Rock",
                "Example Radio",
                "https://example.com",
                true,
                16000);

        ShoutcastInfo info = new ShoutcastInfo(icyHeaders);

        assertEquals(320, info.bitrate);
        assertEquals(16000, info.metadataOffset);
        assertTrue(info.hasMetadata());
    }

    @Test
    void observedFormatFillsMissingSourceDetailsWithoutOverwritingHeaderBitrate() {
        ShoutcastInfo info = new ShoutcastInfo();
        info.bitrate = 320;

        info.mergeObservedAudioFormat("audio/mp4a-latm", "mp4a.40.2", 44100, 2, 192000);

        assertEquals(320, info.bitrate);
        assertEquals(44100, info.sampleRate);
        assertEquals(2, info.channels);
        assertEquals("AAC-LC", info.getCodecDisplayName());
        assertTrue(info.hasUsefulInfo());
    }

    @Test
    void observedFormatConvertsDecoderBitrateAndRecognizesHeAac() {
        ShoutcastInfo info = new ShoutcastInfo();

        info.mergeObservedAudioFormat("audio/mp4a-latm", "mp4a.40.5", 48000, 2, 256000);

        assertEquals(256, info.bitrate);
        assertEquals("HE-AAC", info.getCodecDisplayName());
    }

    private static Response response(Headers headers) {
        return new Response.Builder()
                .request(new Request.Builder().url("https://example.com/stream").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .headers(headers)
                .build();
    }
}
