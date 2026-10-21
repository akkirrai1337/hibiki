package android.net;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * android.net.Uri over the plain RFC 3986 split: scheme, authority, path, query, fragment. Like
 * Android's, parsing never fails - an address with a space or a stray character still parses.
 */
public class Uri {
    private final String text;
    private final String scheme;
    private final String authority;
    private final String path;
    private final String query;
    private final String fragment;

    private Uri(String text, String scheme, String authority, String path, String query, String fragment) {
        this.text = text;
        this.scheme = scheme;
        this.authority = authority;
        this.path = path;
        this.query = query;
        this.fragment = fragment;
    }

    public static Uri parse(String value) {
        String rest = value;
        String fragment = null;
        int hash = rest.indexOf('#');
        if (hash >= 0) { fragment = rest.substring(hash + 1); rest = rest.substring(0, hash); }
        String query = null;
        int question = rest.indexOf('?');
        if (question >= 0) { query = rest.substring(question + 1); rest = rest.substring(0, question); }
        String scheme = null;
        int colon = rest.indexOf(':');
        if (colon > 0 && rest.indexOf('/') != 0) {
            int slash = rest.indexOf('/');
            if (slash < 0 || colon < slash) { scheme = rest.substring(0, colon); rest = rest.substring(colon + 1); }
        }
        String authority = null;
        if (rest.startsWith("//")) {
            int end = rest.indexOf('/', 2);
            authority = end < 0 ? rest.substring(2) : rest.substring(2, end);
            rest = end < 0 ? "" : rest.substring(end);
        }
        return new Uri(value, scheme, authority, rest, query, fragment);
    }

    public static Uri fromFile(File file) {
        String absolute = file.getAbsolutePath().replace('\\', '/');
        if (!absolute.startsWith("/")) absolute = "/" + absolute;
        return parse("file://" + encode(absolute, "/"));
    }

    public static String encode(String value) { return encode(value, null); }

    public static String encode(String value, String allow) {
        if (value == null) return null;
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            boolean safe = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || "_-!.~'()*".indexOf(c) >= 0 || (allow != null && allow.indexOf(c) >= 0);
            if (safe) out.append(c);
            else out.append('%').append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16))).append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
        }
        return out.toString();
    }

    public static String decode(String value) { return decode(value, false); }

    private static String decode(String value, boolean plusAsSpace) {
        if (value == null) return null;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length() && Character.digit(value.charAt(i + 1), 16) >= 0 && Character.digit(value.charAt(i + 2), 16) >= 0) {
                bytes.write(Character.digit(value.charAt(i + 1), 16) * 16 + Character.digit(value.charAt(i + 2), 16));
                i += 2;
            } else if (plusAsSpace && c == '+') {
                bytes.write(' ');
            } else {
                // By code point, so a surrogate pair is encoded as the one character it is.
                int codePoint = value.codePointAt(i);
                byte[] encoded = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
                bytes.write(encoded, 0, encoded.length);
                i += Character.charCount(codePoint) - 1;
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    public String getScheme() { return scheme; }

    public String getAuthority() { return authority == null ? null : decode(authority); }

    public String getEncodedAuthority() { return authority; }

    public String getHost() {
        if (authority == null) return null;
        String host = authority;
        int at = host.lastIndexOf('@');
        if (at >= 0) host = host.substring(at + 1);
        if (host.startsWith("[")) {
            int close = host.indexOf(']');
            return close < 0 ? host : host.substring(0, close + 1);
        }
        int colon = host.lastIndexOf(':');
        return colon >= 0 ? host.substring(0, colon) : host;
    }

    public int getPort() {
        if (authority == null) return -1;
        String host = authority.substring(authority.lastIndexOf('@') + 1);
        int colon = host.lastIndexOf(':');
        if (colon < 0 || host.endsWith("]")) return -1;
        try { return Integer.parseInt(host.substring(colon + 1)); } catch (NumberFormatException e) { return -1; }
    }

    public String getPath() { return decode(path); }

    public String getEncodedPath() { return path; }

    public String getQuery() { return query == null ? null : decode(query); }

    public String getEncodedQuery() { return query; }

    public String getFragment() { return fragment == null ? null : decode(fragment); }

    public String getLastPathSegment() {
        List<String> segments = getPathSegments();
        return segments.isEmpty() ? null : segments.get(segments.size() - 1);
    }

    public List<String> getPathSegments() {
        List<String> out = new ArrayList<>();
        for (String part : path.split("/")) if (!part.isEmpty()) out.add(decode(part));
        return out;
    }

    public boolean isAbsolute() { return scheme != null; }

    public boolean isHierarchical() { return scheme == null || path.startsWith("/") || authority != null; }

    public String getQueryParameter(String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String name = decode(eq < 0 ? pair : pair.substring(0, eq), true);
            if (name.equals(key)) return eq < 0 ? "" : decode(pair.substring(eq + 1), true);
        }
        return null;
    }

    public List<String> getQueryParameters(String key) {
        List<String> out = new ArrayList<>();
        if (query == null) return out;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (decode(eq < 0 ? pair : pair.substring(0, eq), true).equals(key)) out.add(eq < 0 ? "" : decode(pair.substring(eq + 1), true));
        }
        return out;
    }

    public Set<String> getQueryParameterNames() {
        Set<String> out = new LinkedHashSet<>();
        if (query == null) return out;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            out.add(decode(eq < 0 ? pair : pair.substring(0, eq), true));
        }
        return out;
    }

    public Builder buildUpon() {
        Builder builder = new Builder();
        builder.scheme = scheme;
        builder.authority = authority;
        builder.path = path;
        builder.query = query;
        builder.fragment = fragment;
        return builder;
    }

    @Override public String toString() { return text; }

    @Override public boolean equals(Object other) { return other instanceof Uri u && u.text.equals(text); }

    @Override public int hashCode() { return text.hashCode(); }

    public static class Builder {
        String scheme, authority, path = "", query, fragment;

        public Builder scheme(String value) { scheme = value; return this; }

        public Builder encodedAuthority(String value) { authority = value; return this; }

        public Builder authority(String value) { authority = value == null ? null : encode(value, ":@[]"); return this; }

        public Builder path(String value) { path = value == null ? "" : encode(value, "/"); return this; }

        public Builder encodedPath(String value) { path = value == null ? "" : value; return this; }

        public Builder appendPath(String segment) {
            path = (path.endsWith("/") ? path : path + "/") + encode(segment);
            return this;
        }

        public Builder encodedQuery(String value) { query = value; return this; }

        public Builder appendQueryParameter(String key, String value) {
            String pair = encode(key) + "=" + encode(value);
            query = query == null || query.isEmpty() ? pair : query + "&" + pair;
            return this;
        }

        public Builder clearQuery() { query = null; return this; }

        public Builder fragment(String value) { fragment = value == null ? null : encode(value); return this; }

        public Uri build() { return parse(toString()); }

        @Override public String toString() {
            StringBuilder out = new StringBuilder();
            if (scheme != null) out.append(scheme).append(':');
            if (authority != null) out.append("//").append(authority);
            out.append(path);
            if (query != null) out.append('?').append(query);
            if (fragment != null) out.append('#').append(fragment);
            return out.toString();
        }
    }
}
