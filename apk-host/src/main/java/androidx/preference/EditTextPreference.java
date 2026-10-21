package androidx.preference;

import android.content.Context;
import android.widget.EditText;

public class EditTextPreference extends DialogPreference {
    public interface OnBindEditTextListener {
        void onBindEditText(EditText editText);
    }

    private String text;
    private boolean textSet;
    private OnBindEditTextListener bindListener;

    public EditTextPreference(Context context) { super(context); }

    public String getText() { return text; }

    public void setText(String text) {
        boolean changed = !java.util.Objects.equals(this.text, text);
        if (changed || !textSet) {
            this.text = text;
            textSet = true;
            persistString(text);
        }
    }

    public void setOnBindEditTextListener(OnBindEditTextListener listener) { this.bindListener = listener; }

    public OnBindEditTextListener getOnBindEditTextListener() { return bindListener; }

    @Override protected void onSetInitialValue(Object defaultValue) {
        setText(getPersistedString(defaultValue instanceof String s ? s : null));
    }
}
