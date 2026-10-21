package android.content;

import android.content.res.Resources;
import java.io.File;

public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public static final int MODE_WORLD_READABLE = 1;
    public static final int MODE_WORLD_WRITEABLE = 2;
    public static final int MODE_MULTI_PROCESS = 4;

    public abstract SharedPreferences getSharedPreferences(String name, int mode);
    public abstract File getFilesDir();
    public abstract File getCacheDir();
    public abstract String getPackageName();
    public abstract Resources getResources();
    public abstract ContentResolver getContentResolver();
    public abstract Context getApplicationContext();

    public void startActivity(Intent intent) {}
}
