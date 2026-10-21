package android.webkit;

public class URLUtil {
    public static boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file://") || lower.startsWith("ftp://");
    }
}
