package android.webkit;

import android.net.Uri;
import java.util.Map;

public interface WebResourceRequest {
    Uri getUrl();
    boolean isForMainFrame();
    String getMethod();
    Map<String, String> getRequestHeaders();
}
