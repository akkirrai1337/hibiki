package androidx.preference;

import android.content.Context;

public abstract class TwoStatePreference extends Preference {
    private boolean checked;

    TwoStatePreference(Context context) { super(context); }

    public boolean isChecked() { return checked; }

    public void setChecked(boolean checked) {
        this.checked = checked;
        persistBoolean(checked);
    }

    @Override protected void onSetInitialValue(Object defaultValue) {
        setChecked(getPersistedBoolean(defaultValue instanceof Boolean b ? b : false));
    }
}
