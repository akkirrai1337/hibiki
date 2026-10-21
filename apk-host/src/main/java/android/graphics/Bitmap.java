package android.graphics;

/** Only a type for the API's signatures (tile thumbnails): the desktop host does not decode images for extensions. */
public class Bitmap {
    public enum Config { ARGB_8888, RGB_565 }

    public int getWidth() { return 0; }

    public int getHeight() { return 0; }
}
