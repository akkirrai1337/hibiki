package android.content;

import android.content.res.Resources;
import java.io.File;

/** Forwards to the wrapped context, the way Android's does; the host's application is the base. */
public class ContextWrapper extends Context {
    private Context base;

    public ContextWrapper(Context base) { this.base = base; }

    protected void attachBaseContext(Context base) { this.base = base; }

    public Context getBaseContext() { return base; }

    private Context base() {
        if (base == null) throw new IllegalStateException("No base context: the host application is not set up");
        return base;
    }

    @Override public SharedPreferences getSharedPreferences(String name, int mode) { return base().getSharedPreferences(name, mode); }
    @Override public File getFilesDir() { return base().getFilesDir(); }
    @Override public File getCacheDir() { return base().getCacheDir(); }
    @Override public String getPackageName() { return base().getPackageName(); }
    @Override public Resources getResources() { return base().getResources(); }
    @Override public ContentResolver getContentResolver() { return base().getContentResolver(); }
    @Override public Context getApplicationContext() { return base().getApplicationContext(); }
    @Override public void startActivity(Intent intent) { if (base != null) base.startActivity(intent); }
}
