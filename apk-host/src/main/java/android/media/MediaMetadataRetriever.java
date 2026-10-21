package android.media;

import java.util.Map;

/**
 * Extensions read a video's duration through this. The desktop host has no media decoder behind
 * it, so every key reads as absent - which callers treat as "unknown".
 */
public class MediaMetadataRetriever {
    public static final int METADATA_KEY_DURATION = 9;

    public void setDataSource(String uri, Map<String, String> headers) {}

    public String extractMetadata(int keyCode) { return null; }

    public void release() {}
}
