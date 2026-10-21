package android.graphics;

import java.io.InputStream;

public class BitmapFactory {
    /** No decoder: a tile an extension asks the host for comes back as "no image". */
    public static Bitmap decodeStream(InputStream stream) { return null; }
}
