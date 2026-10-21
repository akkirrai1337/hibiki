package android.webkit;

import java.io.InputStream;
import java.util.Map;

public class WebResourceResponse {
    private final String mimeType;
    private final String encoding;
    private final InputStream data;
    private int statusCode = 200;
    private String reasonPhrase = "OK";
    private Map<String, String> headers;

    public WebResourceResponse(String mimeType, String encoding, InputStream data) {
        this.mimeType = mimeType;
        this.encoding = encoding;
        this.data = data;
    }

    public WebResourceResponse(String mimeType, String encoding, int statusCode, String reasonPhrase, Map<String, String> headers, InputStream data) {
        this(mimeType, encoding, data);
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.headers = headers;
    }

    public String getMimeType() { return mimeType; }
    public String getEncoding() { return encoding; }
    public InputStream getData() { return data; }
    public int getStatusCode() { return statusCode; }
    public String getReasonPhrase() { return reasonPhrase; }
    public Map<String, String> getResponseHeaders() { return headers; }
}
