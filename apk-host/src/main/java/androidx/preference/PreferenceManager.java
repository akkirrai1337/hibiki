package androidx.preference;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private final Context context;
    private String sharedPreferencesName;

    public PreferenceManager(Context context) {
        this.context = context;
        this.sharedPreferencesName = defaultName(context);
    }

    public static String defaultName(Context context) { return context.getPackageName() + "_preferences"; }

    public static SharedPreferences getDefaultSharedPreferences(Context context) {
        return context.getSharedPreferences(defaultName(context), Context.MODE_PRIVATE);
    }

    public void setSharedPreferencesName(String name) { this.sharedPreferencesName = name; }

    public String getSharedPreferencesName() { return sharedPreferencesName; }

    public SharedPreferences getSharedPreferences() {
        return context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE);
    }

    public PreferenceScreen createPreferenceScreen(Context context) { return new PreferenceScreen(context, this); }
}
