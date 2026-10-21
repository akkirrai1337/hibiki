package android.text;

import org.jsoup.Jsoup;

public class Html {
    public static final int FROM_HTML_MODE_LEGACY = 0;
    public static final int FROM_HTML_MODE_COMPACT = 63;

    /** The text of the markup: the host has no styled-text view, so spans are not kept. */
    public static Spanned fromHtml(String source, int flags) {
        String text = Jsoup.parse(source == null ? "" : source).wholeText();
        return new Plain(text);
    }

    private record Plain(String text) implements Spanned {
        @Override public int length() { return text.length(); }
        @Override public char charAt(int index) { return text.charAt(index); }
        @Override public CharSequence subSequence(int start, int end) { return text.subSequence(start, end); }
        @Override public String toString() { return text; }
    }
}
