package androidx.preference;

import android.content.Context;

public class ListPreference extends DialogPreference {
    private CharSequence[] entries;
    private CharSequence[] entryValues;
    private String value;
    private boolean valueSet;

    public ListPreference(Context context) { super(context); }

    public CharSequence[] getEntries() { return entries; }

    public void setEntries(CharSequence[] entries) { this.entries = entries; }

    public CharSequence[] getEntryValues() { return entryValues; }

    public void setEntryValues(CharSequence[] entryValues) { this.entryValues = entryValues; }

    public String getValue() { return value; }

    public void setValue(String value) {
        boolean changed = !java.util.Objects.equals(this.value, value);
        if (changed || !valueSet) {
            this.value = value;
            valueSet = true;
            persistString(value);
        }
    }

    public int findIndexOfValue(String value) {
        if (value != null && entryValues != null) {
            for (int i = entryValues.length - 1; i >= 0; i--) if (entryValues[i].toString().equals(value)) return i;
        }
        return -1;
    }

    public CharSequence getEntry() {
        int index = findIndexOfValue(value);
        return index >= 0 && entries != null && index < entries.length ? entries[index] : null;
    }

    /** A summary with "%s" shows the chosen entry there, as androidx's does. */
    @Override public CharSequence getSummary() {
        CharSequence summary = super.getSummary();
        if (summary == null) return null;
        CharSequence entry = getEntry();
        try {
            return String.format(summary.toString(), entry == null ? "" : entry);
        } catch (java.util.IllegalFormatException e) {
            return summary;
        }
    }

    @Override protected void onSetInitialValue(Object defaultValue) {
        setValue(getPersistedString(defaultValue instanceof String s ? s : null));
    }
}
