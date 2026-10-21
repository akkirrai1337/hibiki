package android.util;

import java.nio.charset.StandardCharsets;

/** Android's flag semantics over java.util.Base64: DEFAULT wraps at 76 columns and ends with a newline. */
public class Base64 {
    public static final int DEFAULT = 0, NO_PADDING = 1, NO_WRAP = 2, CRLF = 4, URL_SAFE = 8, NO_CLOSE = 16;

    public static byte[] decode(String str, int flags) { return decode(str.getBytes(StandardCharsets.ISO_8859_1), flags); }

    public static byte[] decode(byte[] input, int flags) {
        StringBuilder clean = new StringBuilder(input.length);
        for (byte b : input) {
            char c = (char) (b & 0xFF);
            if (c != '\n' && c != '\r' && c != ' ' && c != '\t') clean.append(c);
        }
        java.util.Base64.Decoder decoder = (flags & URL_SAFE) != 0 ? java.util.Base64.getUrlDecoder() : java.util.Base64.getDecoder();
        return decoder.decode(clean.toString());
    }

    public static byte[] encode(byte[] input, int flags) {
        return encodeToString(input, flags).getBytes(StandardCharsets.ISO_8859_1);
    }

    public static String encodeToString(byte[] input, int flags) {
        java.util.Base64.Encoder encoder = (flags & URL_SAFE) != 0 ? java.util.Base64.getUrlEncoder() : java.util.Base64.getEncoder();
        if ((flags & NO_PADDING) != 0) encoder = encoder.withoutPadding();
        String text = encoder.encodeToString(input);
        if ((flags & NO_WRAP) != 0) return text;
        String newline = (flags & CRLF) != 0 ? "\r\n" : "\n";
        StringBuilder wrapped = new StringBuilder(text.length() + text.length() / 76 * 2 + 2);
        for (int i = 0; i < text.length(); i += 76) wrapped.append(text, i, Math.min(text.length(), i + 76)).append(newline);
        return wrapped.toString();
    }

    public static String encodeToString(byte[] input, int offset, int len, int flags) {
        return encodeToString(java.util.Arrays.copyOfRange(input, offset, offset + len), flags);
    }
}
