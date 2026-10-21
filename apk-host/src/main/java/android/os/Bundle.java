package android.os;

import java.util.HashMap;
import java.util.Map;

public class Bundle {
    private final Map<String, Object> values = new HashMap<>();

    public void putString(String key, String value) { values.put(key, value); }

    public String getString(String key) { return values.get(key) instanceof String s ? s : null; }

    public String getString(String key, String fallback) { String s = getString(key); return s != null ? s : fallback; }

    public void putInt(String key, int value) { values.put(key, value); }

    public int getInt(String key, int fallback) { return values.get(key) instanceof Integer i ? i : fallback; }

    public boolean containsKey(String key) { return values.containsKey(key); }
}
