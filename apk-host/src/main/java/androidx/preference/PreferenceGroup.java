package androidx.preference;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public abstract class PreferenceGroup extends Preference {
    private final List<Preference> children = new ArrayList<>();

    PreferenceGroup(Context context) { super(context); }

    public boolean addPreference(Preference preference) {
        children.add(preference);
        if (manager != null) preference.attach(manager);
        return true;
    }

    public boolean removePreference(Preference preference) { return children.remove(preference); }

    public void removeAll() { children.clear(); }

    public int getPreferenceCount() { return children.size(); }

    public Preference getPreference(int index) { return children.get(index); }

    public Preference findPreference(CharSequence key) {
        for (Preference child : children) {
            if (key.equals(child.getKey())) return child;
            if (child instanceof PreferenceGroup group) {
                Preference found = group.findPreference(key);
                if (found != null) return found;
            }
        }
        return null;
    }
}
