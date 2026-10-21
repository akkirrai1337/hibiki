package androidx.preference;

import android.content.Context;

public final class PreferenceScreen extends PreferenceGroup {
    PreferenceScreen(Context context, PreferenceManager manager) {
        super(context);
        this.manager = manager;
    }
}
