package android.view;

/** Layout widgets are never shown on the desktop; they exist so extension classes that mention them link. */
public class View {
    private boolean enabled = true;

    public View findViewById(int id) { return null; }

    public View getRootView() { return this; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isEnabled() { return enabled; }

    public void measure(int widthMeasureSpec, int heightMeasureSpec) {}

    public void layout(int left, int top, int right, int bottom) {}

    public static class MeasureSpec {
        public static final int UNSPECIFIED = 0, EXACTLY = 1 << 30, AT_MOST = 2 << 30;

        public static int makeMeasureSpec(int size, int mode) { return (size & 0x3FFFFFFF) | mode; }
    }
}
