package android.os;

public class SystemClock {
    private static final long START = System.nanoTime();

    public static long elapsedRealtime() { return (System.nanoTime() - START) / 1_000_000L; }

    public static long uptimeMillis() { return elapsedRealtime(); }
}
