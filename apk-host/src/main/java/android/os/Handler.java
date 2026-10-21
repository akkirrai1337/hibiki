package android.os;

import java.util.concurrent.TimeUnit;

public class Handler {
    private final Looper looper;

    public Handler() { this(Looper.getMainLooper()); }

    public Handler(Looper looper) { this.looper = looper; }

    public boolean post(Runnable runnable) { looper.executor.execute(runnable); return true; }

    public boolean postDelayed(Runnable runnable, long delayMillis) {
        looper.executor.schedule(runnable, delayMillis, TimeUnit.MILLISECONDS);
        return true;
    }
}
