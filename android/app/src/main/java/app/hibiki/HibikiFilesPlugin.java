package app.hibiki;

import android.util.Base64;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * FilesPort for Android: absolute paths under the app's private storage. Same rules as the
 * desktop adapter (src/platform/contract/files.contract.ts): a missing path is "absent", never an
 * error, and directories are created and removed recursively.
 */
@CapacitorPlugin(name = "HibikiFiles")
public class HibikiFilesPlugin extends Plugin {

    interface Job {
        void run(PluginCall call) throws Exception;
    }

    private void io(PluginCall call, Job job) {
        Http.io.execute(() -> {
            try {
                job.run(call);
            } catch (Exception e) {
                call.reject(e.getMessage() == null ? String.valueOf(e) : e.getMessage(), e);
            }
        });
    }

    private static File file(PluginCall call, String key) {
        return new File(call.getString(key));
    }

    @PluginMethod
    public void dataDir(PluginCall call) {
        JSObject result = new JSObject();
        result.put("path", getContext().getFilesDir().getAbsolutePath());
        call.resolve(result);
    }

    @PluginMethod
    public void exists(PluginCall call) {
        io(call, c -> {
            JSObject result = new JSObject();
            result.put("value", file(c, "path").exists());
            c.resolve(result);
        });
    }

    @PluginMethod
    public void stat(PluginCall call) {
        io(call, c -> {
            File f = file(c, "path");
            JSObject result = new JSObject();
            if (f.exists()) {
                result.put("size", f.length());
                result.put("mtimeMs", f.lastModified());
                result.put("isDirectory", f.isDirectory());
            } else {
                result.put("missing", true);
            }
            c.resolve(result);
        });
    }

    @PluginMethod
    public void list(PluginCall call) {
        io(call, c -> {
            String[] names = file(c, "path").list();
            JSArray entries = new JSArray();
            if (names != null) for (String name : names) entries.put(name);
            JSObject result = new JSObject();
            result.put("entries", entries);
            c.resolve(result);
        });
    }

    @PluginMethod
    public void readText(PluginCall call) {
        io(call, c -> {
            JSObject result = new JSObject();
            result.put("value", new String(Files.readAllBytes(file(c, "path").toPath()), StandardCharsets.UTF_8));
            c.resolve(result);
        });
    }

    @PluginMethod
    public void writeText(PluginCall call) {
        io(call, c -> {
            write(file(c, "path"), c.getString("value", "").getBytes(StandardCharsets.UTF_8));
            c.resolve();
        });
    }

    /** Adds to the end of a file, creating it - the log, which grows a line at a time. */
    @PluginMethod
    public void appendText(PluginCall call) {
        io(call, c -> {
            try (FileOutputStream out = new FileOutputStream(file(c, "path"), true)) {
                out.write(c.getString("value", "").getBytes(StandardCharsets.UTF_8));
            }
            c.resolve();
        });
    }

    @PluginMethod
    public void readBytes(PluginCall call) {
        io(call, c -> {
            JSObject result = new JSObject();
            result.put("base64", Base64.encodeToString(Files.readAllBytes(file(c, "path").toPath()), Base64.NO_WRAP));
            c.resolve(result);
        });
    }

    @PluginMethod
    public void writeBytes(PluginCall call) {
        io(call, c -> {
            write(file(c, "path"), Base64.decode(c.getString("base64", ""), Base64.DEFAULT));
            c.resolve();
        });
    }

    @PluginMethod
    public void mkdir(PluginCall call) {
        io(call, c -> {
            File dir = file(c, "path");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("could not create " + dir);
            c.resolve();
        });
    }

    @PluginMethod
    public void remove(PluginCall call) {
        io(call, c -> {
            delete(file(c, "path"));
            c.resolve();
        });
    }

    @PluginMethod
    public void rename(PluginCall call) {
        io(call, c -> {
            File from = file(c, "from");
            File to = file(c, "to");
            if (to.exists()) delete(to);
            if (!from.renameTo(to)) throw new IOException("could not rename " + from + " to " + to);
            c.resolve();
        });
    }

    private static void write(File target, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(target, false)) {
            out.write(data);
        }
    }

    private static void delete(File target) throws IOException {
        if (!target.exists()) return;
        File[] children = target.listFiles();
        if (children != null) for (File child : children) delete(child);
        if (!target.delete() && target.exists()) throw new IOException("could not remove " + target);
    }
}
