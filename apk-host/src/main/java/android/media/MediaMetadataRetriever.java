package android.media;

import app.hibiki.apkhost.MediaProbe;
import java.util.Collections;
import java.util.Map;

/**
 * What extensions ask of a video: its duration. The desktop host has no media framework, so the length
 * is read from the file's own header (see MediaProbe) - MP4/MOV, Matroska/WebM and HLS playlists. Any
 * other key, and any file that cannot be read, answers null, which callers treat as "unknown".
 */
public class MediaMetadataRetriever {
    public static final int METADATA_KEY_DURATION = 9;

    private Long durationMs;

    public void setDataSource(String uri) { setDataSource(uri, Collections.emptyMap()); }

    public void setDataSource(String uri, Map<String, String> headers) {
        durationMs = MediaProbe.INSTANCE.durationMs(uri, headers == null ? Collections.emptyMap() : headers);
    }

    public String extractMetadata(int keyCode) {
        return keyCode == METADATA_KEY_DURATION && durationMs != null ? Long.toString(durationMs) : null;
    }

    public void release() { durationMs = null; }
}
