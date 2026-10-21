package android.app;

import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;

/** Extensions' URL-handler activities are never started on the desktop; the class only has to link. */
public class Activity extends ContextWrapper {
    private Intent intent = new Intent();

    public Activity() { super(null); }

    protected void onCreate(Bundle savedInstanceState) {}

    public Intent getIntent() { return intent; }

    public void setIntent(Intent intent) { this.intent = intent; }

    public void finish() {}
}
