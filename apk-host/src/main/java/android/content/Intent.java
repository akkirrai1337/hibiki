package android.content;

import android.net.Uri;
import java.util.HashMap;
import java.util.Map;

public class Intent {
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public static final String ACTION_MAIN = "android.intent.action.MAIN";

    private String action;
    private Uri data;
    private final Map<String, Object> extras = new HashMap<>();

    public Intent() {}

    public Intent(String action) { this.action = action; }

    public Intent setAction(String action) { this.action = action; return this; }

    public String getAction() { return action; }

    public Uri getData() { return data; }

    public Intent setData(Uri data) { this.data = data; return this; }

    public Intent putExtra(String name, String value) { extras.put(name, value); return this; }

    public String getStringExtra(String name) { return extras.get(name) instanceof String s ? s : null; }
}
