package android.os;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** The "main thread" extensions post to: one background thread, since the desktop host has no UI thread of its own. */
public class Looper {
    private static final Looper MAIN = new Looper();

    final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "android-main");
        thread.setDaemon(true);
        return thread;
    });

    public static Looper getMainLooper() { return MAIN; }

    public static Looper myLooper() { return MAIN; }
}
