package android.widget;

import android.content.Context;
import android.util.Log;

public class Toast {
    public static final int LENGTH_SHORT = 0, LENGTH_LONG = 1;

    private final CharSequence text;

    private Toast(CharSequence text) { this.text = text; }

    public static Toast makeText(Context context, CharSequence text, int duration) { return new Toast(text); }

    /** There is no screen of the host's own to show it on: it goes to the log. */
    public void show() { Log.i("Toast", String.valueOf(text)); }
}
