package android.content;

public class ActivityNotFoundException extends RuntimeException {
    public ActivityNotFoundException() {}

    public ActivityNotFoundException(String message) { super(message); }
}
