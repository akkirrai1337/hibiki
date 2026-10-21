package androidx.preference;

import android.content.Context;
import java.util.HashSet;
import java.util.Set;

public class MultiSelectListPreference extends DialogPreference {
    private CharSequence[] entries;
    private CharSequence[] entryValues;
    private Set<String> values = new HashSet<>();

    public MultiSelectListPreference(Context context) { super(context); }

    public CharSequence[] getEntries() { return entries; }

    public void setEntries(CharSequence[] entries) { this.entries = entries; }

    public CharSequence[] getEntryValues() { return entryValues; }

    public void setEntryValues(CharSequence[] entryValues) { this.entryValues = entryValues; }

    public Set<String> getValues() { return values; }

    public void setValues(Set<String> values) {
        this.values = new HashSet<>(values);
        persistStringSet(this.values);
    }

    public int findIndexOfValue(String value) {
        if (value != null && entryValues != null) {
            for (int i = entryValues.length - 1; i >= 0; i--) if (entryValues[i].toString().equals(value)) return i;
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    @Override protected void onSetInitialValue(Object defaultValue) {
        setValues(getPersistedStringSet(defaultValue instanceof Set<?> set ? (Set<String>) set : new HashSet<>()));
    }
}
