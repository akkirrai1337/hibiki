package android.util;

import java.io.PrintStream;

public class Log {
    public static final int VERBOSE = 2, DEBUG = 3, INFO = 4, WARN = 5, ERROR = 6;

    /** Where log lines go; stderr by default, because stdout belongs to the host's protocol. */
    public static volatile PrintStream out = System.err;
    public static volatile int minLevel = INFO;

    private static int log(int level, String tag, String message, Throwable error) {
        if (level < minLevel) return 0;
        String name = level == ERROR ? "E" : level == WARN ? "W" : level == INFO ? "I" : level == DEBUG ? "D" : "V";
        out.println(name + "/" + tag + ": " + message);
        if (error != null) error.printStackTrace(out);
        return 0;
    }

    public static int v(String tag, String msg) { return log(VERBOSE, tag, msg, null); }
    public static int d(String tag, String msg) { return log(DEBUG, tag, msg, null); }
    public static int d(String tag, String msg, Throwable error) { return log(DEBUG, tag, msg, error); }
    public static int i(String tag, String msg, Throwable error) { return log(INFO, tag, msg, error); }
    public static int i(String tag, String msg) { return log(INFO, tag, msg, null); }
    public static int w(String tag, String msg) { return log(WARN, tag, msg, null); }
    public static int w(String tag, String msg, Throwable error) { return log(WARN, tag, msg, error); }
    public static int w(String tag, Throwable error) { return log(WARN, tag, "", error); }
    public static int e(String tag, String msg) { return log(ERROR, tag, msg, null); }
    public static int e(String tag, String msg, Throwable error) { return log(ERROR, tag, msg, error); }

    public static String getStackTraceString(Throwable error) {
        java.io.StringWriter writer = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }
}
