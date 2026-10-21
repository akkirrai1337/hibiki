package android.widget;

import android.text.TextWatcher;
import android.view.View;

public class TextView extends View {
    private CharSequence error;

    public void addTextChangedListener(TextWatcher watcher) {}

    public CharSequence getError() { return error; }

    public void setError(CharSequence error) { this.error = error; }

    public void setInputType(int type) {}
}
