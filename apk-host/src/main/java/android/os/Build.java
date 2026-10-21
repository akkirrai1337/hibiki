package android.os;

/** The "device" an extension sees: a recent Android, so its version checks take the modern path. */
public class Build {
    public static final String MANUFACTURER = "Hibiki";
    public static final String MODEL = "Desktop";
    public static final String BRAND = "hibiki";

    public static class VERSION {
        public static final int SDK_INT = 34;
        public static final String RELEASE = "14";
    }

    public static class VERSION_CODES {
        public static final int N = 24;
        public static final int O = 26;
        public static final int P = 28;
        public static final int Q = 29;
        public static final int R = 30;
        public static final int S = 31;
        public static final int TIRAMISU = 33;
    }
}
