package androidx.preference;

import android.content.Context;

/** The dialog texts are kept only because extensions set them; the desktop settings rows have no dialog of their own. */
public abstract class DialogPreference extends Preference {
    private CharSequence dialogTitle;
    private CharSequence dialogMessage;

    DialogPreference(Context context) { super(context); }

    public CharSequence getDialogTitle() { return dialogTitle; }

    public void setDialogTitle(CharSequence title) { this.dialogTitle = title; }

    public CharSequence getDialogMessage() { return dialogMessage; }

    public void setDialogMessage(CharSequence message) { this.dialogMessage = message; }
}
