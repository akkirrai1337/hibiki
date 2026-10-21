package androidx.preference;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Set;

/**
 * A setting an extension declares on its screen. Follows androidx's behaviour where extensions can
 * see it: values persist into the screen's SharedPreferences as soon as they are set, the default
 * is applied when the preference joins a screen that has no stored value for its key, and a change
 * listener may veto a change before it is stored.
 */
public class Preference {
    public interface OnPreferenceChangeListener {
        boolean onPreferenceChange(Preference preference, Object newValue);
    }

    private final Context context;
    private String key;
    private CharSequence title;
    private CharSequence summary;
    private Object defaultValue;
    private boolean enabled = true;
    private boolean visible = true;
    private OnPreferenceChangeListener changeListener;
    PreferenceManager manager;

    public Preference(Context context) { this.context = context; }

    public Context getContext() { return context; }

    public String getKey() { return key; }

    public void setKey(String key) { this.key = key; }

    public CharSequence getTitle() { return title; }

    public void setTitle(CharSequence title) { this.title = title; }

    public CharSequence getSummary() { return summary; }

    public void setSummary(CharSequence summary) { this.summary = summary; }

    public Object getDefaultValue() { return defaultValue; }

    public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isVisible() { return visible; }

    public void setVisible(boolean visible) { this.visible = visible; }

    public void setOnPreferenceChangeListener(OnPreferenceChangeListener listener) { this.changeListener = listener; }

    public OnPreferenceChangeListener getOnPreferenceChangeListener() { return changeListener; }

    public boolean callChangeListener(Object newValue) {
        return changeListener == null || changeListener.onPreferenceChange(this, newValue);
    }

    // --- persistence -----------------------------------------------------------------------------

    protected SharedPreferences getSharedPreferences() {
        if (manager == null) throw new IllegalStateException("The preference is not on a screen yet");
        return manager.getSharedPreferences();
    }

    private boolean shouldPersist() { return manager != null && key != null && !key.isEmpty(); }

    protected boolean persistString(String value) {
        if (!shouldPersist()) return false;
        getSharedPreferences().edit().putString(key, value).apply();
        return true;
    }

    protected String getPersistedString(String fallback) {
        return shouldPersist() ? getSharedPreferences().getString(key, fallback) : fallback;
    }

    protected boolean persistBoolean(boolean value) {
        if (!shouldPersist()) return false;
        getSharedPreferences().edit().putBoolean(key, value).apply();
        return true;
    }

    protected boolean getPersistedBoolean(boolean fallback) {
        return shouldPersist() ? getSharedPreferences().getBoolean(key, fallback) : fallback;
    }

    protected boolean persistStringSet(Set<String> values) {
        if (!shouldPersist()) return false;
        getSharedPreferences().edit().putStringSet(key, values).apply();
        return true;
    }

    protected Set<String> getPersistedStringSet(Set<String> fallback) {
        return shouldPersist() ? getSharedPreferences().getStringSet(key, fallback) : fallback;
    }

    /** Takes the stored value in, or the default when nothing is stored: what androidx does on attach. */
    protected void onSetInitialValue(Object defaultValue) {}

    final void attach(PreferenceManager manager) {
        this.manager = manager;
        boolean stored = shouldPersist() && getSharedPreferences().contains(key);
        if (!stored && defaultValue != null) onSetInitialValue(defaultValue);
        else onSetInitialValue(null);
    }
}
