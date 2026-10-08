package app.hibiki;

import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Rational;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.view.Window;
import android.view.WindowManager;
import androidx.activity.result.ActivityResult;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The few things about the app's own window the mobile UI asks for: leaving to the launcher when
 * Back has nowhere left to go, keeping the screen on while a video plays, and turning the screen
 * for the player. The Back key itself arrives as a "hibikiback" window event (see MainActivity).
 */
@CapacitorPlugin(name = "HibikiApp")
public class HibikiAppPlugin extends Plugin {

    /**
     * What the Kotlin hibiki left behind - the same package, so its private files are this app's now:
     * the rows of its library database and its saved episode positions. Read only and as they are;
     * core/legacyImport.ts decides what they mean here. Off the main thread: a long history is a big
     * preferences file.
     */
    @PluginMethod
    public void legacyData(PluginCall call) {
        new Thread(() -> {
            JSObject result = new JSObject();
            com.getcapacitor.JSArray library = new com.getcapacitor.JSArray();
            java.io.File database = getContext().getDatabasePath("hibiki_library.db");
            if (database.exists()) {
                try (android.database.sqlite.SQLiteDatabase sqlite = android.database.sqlite.SQLiteDatabase.openDatabase(
                        database.getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
                     android.database.Cursor rows = sqlite.rawQuery("SELECT title_id, anime_json, categories, added_at FROM library_entries", null)) {
                    while (rows.moveToNext()) {
                        JSObject row = new JSObject();
                        row.put("titleId", rows.getString(0));
                        if (!rows.isNull(1)) row.put("animeJson", rows.getString(1));
                        row.put("categories", rows.getString(2));
                        if (!rows.isNull(3)) row.put("addedAt", rows.getLong(3));
                        library.put(row);
                    }
                } catch (Exception error) {
                    result.put("libraryError", String.valueOf(error));
                }
            }
            JSObject progress = new JSObject();
            for (java.util.Map.Entry<String, ?> entry : getContext().getSharedPreferences("hibiki_watch_state", Context.MODE_PRIVATE).getAll().entrySet()) {
                if (entry.getKey().startsWith("progress_") && entry.getValue() instanceof String) progress.put(entry.getKey(), (String) entry.getValue());
            }
            result.put("found", database.exists() || progress.length() > 0);
            result.put("library", library);
            result.put("progress", progress);
            call.resolve(result);
        }).start();
    }

    /** Back on the first screen: to the launcher, as other apps do, without destroying the activity. */
    @PluginMethod
    public void minimize(PluginCall call) {
        getActivity().runOnUiThread(() -> getActivity().moveTaskToBack(true));
        call.resolve();
    }

    @PluginMethod
    public void keepAwake(PluginCall call) {
        boolean on = Boolean.TRUE.equals(call.getBoolean("value", false));
        getActivity().runOnUiThread(() -> {
            if (on) getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
        call.resolve();
    }

    /** Whether the player asked for the system bars out of the way - re-applied after anything that brings them back. */
    private boolean immersive = false;

    /**
     * The player's full screen: status and navigation bars hidden, a swipe from the edge shows them
     * for a moment and they hide again. Turning the screen (which the player itself asks for) and
     * coming back to the app both bring the bars back on some systems, so it is applied again then.
     */
    @PluginMethod
    public void setImmersive(PluginCall call) {
        immersive = Boolean.TRUE.equals(call.getBoolean("value", false));
        getActivity().runOnUiThread(this::applyImmersive);
        call.resolve();
    }

    private void applyImmersive() {
        Window window = getActivity().getWindow();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (immersive) {
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    @Override
    protected void handleOnConfigurationChanged(Configuration newConfig) {
        super.handleOnConfigurationChanged(newConfig);
        if (immersive) getActivity().getWindow().getDecorView().post(this::applyImmersive);
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        if (immersive) getActivity().getWindow().getDecorView().post(this::applyImmersive);
    }

    /**
     * "landscape" (either way up, following the sensor), "portrait", "sensor" (any way up, following
     * the sensor even where the system's own rotation lock is on) or "auto" for the user's own setting.
     */
    @PluginMethod
    public void setOrientation(PluginCall call) {
        String value = call.getString("value", "auto");
        int orientation;
        switch (value) {
            case "landscape":
                orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
                break;
            case "portrait":
                orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                break;
            case "sensor":
                orientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
                break;
            default:
                orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
        getActivity().runOnUiThread(() -> getActivity().setRequestedOrientation(orientation));
        call.resolve();
    }

    /**
     * Hands a text file to the system share sheet - the phone's "save as" for the exported log:
     * from there it goes to a messenger, the mail, or Files. Written under the cache dir, which
     * the FileProvider already exposes (res/xml/file_paths.xml).
     */
    @PluginMethod
    public void shareText(PluginCall call) {
        String name = call.getString("name", "hibiki.txt").replaceAll("[\\\\/]", "_");
        String text = call.getString("text", "");
        try {
            File dir = new File(getContext().getCacheDir(), "shared");
            dir.mkdirs();
            File file = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            android.net.Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
            // The file alone: no EXTRA_SUBJECT/EXTRA_TEXT, which Telegram and others post as a caption
            // under it. ClipData carries the read grant through the chooser to the picked app.
            Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(android.content.ClipData.newRawUri(name, uri));
            Intent chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getActivity().runOnUiThread(() -> getActivity().startActivity(chooser));
            call.resolve();
        } catch (Exception error) {
            call.reject(error.getMessage(), error);
        }
    }

    // --- picture in picture --------------------------------------------------------------------
    // As the Kotlin app has it: the player's PiP button (and, on Android 12+, leaving the app while
    // a video plays) shrinks the app to a small window with the picture, whose buttons are the
    // previous episode, play/pause, the next episode and audio only (the window goes, the sound
    // stays). The page keeps this up to date (updatePip) and hears the buttons as "pipAction";
    // the mode itself arrives as the "hibikipip" window event (MainActivity).

    private static final String PIP_ACTION = "app.hibiki.PIP_ACTION";
    private boolean pipEnabled = false;
    private boolean pipPlaying = false;
    private boolean pipHasPrevious = false;
    private boolean pipHasNext = false;
    private int pipWidth = 16;
    private int pipHeight = 9;
    private JSObject pipLabels = new JSObject();
    private BroadcastReceiver pipReceiver;

    /** A hibiki:// link the app was started with, kept until the page asks for it (takeLaunchUrl). */
    private String launchUrl;

    @Override
    public void load() {
        launchUrl = deepLinkOf(getActivity().getIntent());
        pipReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                JSObject event = new JSObject();
                event.put("action", intent.getStringExtra("action"));
                notifyListeners("pipAction", event);
            }
        };
        ContextCompat.registerReceiver(getContext(), pipReceiver, new IntentFilter(PIP_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /** A hibiki:// link arriving while the app runs (singleTask: the same activity gets it). */
    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        String url = deepLinkOf(intent);
        if (url == null) return;
        JSObject event = new JSObject();
        event.put("url", url);
        notifyListeners("deepLink", event, true);
    }

    /** The link the app was started with, once: a later call answers null. */
    @PluginMethod
    public void takeLaunchUrl(PluginCall call) {
        JSObject result = new JSObject();
        result.put("url", launchUrl);
        launchUrl = null;
        call.resolve(result);
    }

    private static String deepLinkOf(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return null;
        String url = intent.getDataString();
        return url != null && url.startsWith("hibiki://") ? url : null;
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        if (pipReceiver != null) {
            try {
                getContext().unregisterReceiver(pipReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already gone with the context.
            }
            pipReceiver = null;
        }
    }

    private boolean pipSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    /** The player's state, for the window's buttons and its shape; enabled = a video is on screen. */
    @PluginMethod
    public void updatePip(PluginCall call) {
        pipEnabled = Boolean.TRUE.equals(call.getBoolean("enabled", false));
        pipPlaying = Boolean.TRUE.equals(call.getBoolean("playing", false));
        pipHasPrevious = Boolean.TRUE.equals(call.getBoolean("hasPrevious", false));
        pipHasNext = Boolean.TRUE.equals(call.getBoolean("hasNext", false));
        Integer width = call.getInt("width");
        Integer height = call.getInt("height");
        if (width != null && height != null && width > 0 && height > 0) {
            pipWidth = width;
            pipHeight = height;
        }
        JSObject labels = call.getObject("labels");
        if (labels != null) pipLabels = labels;
        getActivity().runOnUiThread(() -> {
            if (!pipSupported()) return;
            try {
                getActivity().setPictureInPictureParams(buildPipParams());
            } catch (IllegalStateException | IllegalArgumentException ignored) {
                // An activity being torn down, or a shape the system refuses: keep the old params.
            }
        });
        call.resolve();
    }

    @PluginMethod
    public void enterPip(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            boolean entered = false;
            if (pipSupported()) {
                try {
                    entered = getActivity().enterPictureInPictureMode(buildPipParams());
                } catch (IllegalStateException | IllegalArgumentException ignored) {
                    entered = false;
                }
            }
            JSObject result = new JSObject();
            result.put("entered", entered);
            call.resolve(result);
        });
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private PictureInPictureParams buildPipParams() {
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
        // The system takes shapes between 1:2.39 and 2.39:1 only.
        float ratio = Math.max(1f / 2.39f, Math.min(2.39f, (float) pipWidth / pipHeight));
        builder.setAspectRatio(new Rational(Math.round(ratio * 1000), 1000));

        List<RemoteAction> actions = new ArrayList<>();
        if (pipHasPrevious) actions.add(pipAction("previous", R.drawable.ic_pip_previous, label("previous", "Previous episode"), 1));
        actions.add(pipPlaying
            ? pipAction("toggle", R.drawable.ic_pip_pause, label("pause", "Pause"), 2)
            : pipAction("toggle", R.drawable.ic_pip_play, label("play", "Play"), 2));
        if (pipHasNext) actions.add(pipAction("next", R.drawable.ic_pip_next, label("next", "Next episode"), 3));
        // Audio only where the window has room for a fourth button (most show three).
        if (actions.size() < getActivity().getMaxNumPictureInPictureActions()) {
            actions.add(0, pipAction("audioOnly", R.drawable.ic_pip_audio_only, label("audioOnly", "Audio only"), 4));
        }
        builder.setActions(actions);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Leaving the app while a video plays takes it along in the small window.
            builder.setAutoEnterEnabled(pipEnabled && pipPlaying);
            builder.setSeamlessResizeEnabled(false);
        }
        return builder.build();
    }

    private String label(String key, String fallback) {
        String value = pipLabels.optString(key, "");
        return value.isEmpty() ? fallback : value;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private RemoteAction pipAction(String action, int icon, String title, int requestCode) {
        Intent intent = new Intent(PIP_ACTION).setPackage(getContext().getPackageName()).putExtra("action", action);
        PendingIntent pending = PendingIntent.getBroadcast(getContext(), requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new RemoteAction(Icon.createWithResource(getContext(), icon), title, title, pending);
    }

    // --- Work that outlives the screen ---------------------------------------------------------

    /** Whether the notification permission was already asked for this run - asked once, never nagged. */
    private boolean notificationsAsked = false;

    /**
     * Downloads running or not: `active` starts or updates the foreground service that keeps the app
     * alive in the background (with its notification), and its absence stops it.
     */
    @PluginMethod
    public void setBackgroundWork(PluginCall call) {
        boolean active = Boolean.TRUE.equals(call.getBoolean("active", false));
        if (!active) {
            BackgroundWorkService.stop(getContext());
            call.resolve();
            return;
        }
        // Without it the service still runs, but its notification - the only sign that the app is
        // busy - stays hidden. Asked for at the moment it starts to matter.
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAsked
            && ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationsAsked = true;
            androidx.core.app.ActivityCompat.requestPermissions(getActivity(), new String[] { android.Manifest.permission.POST_NOTIFICATIONS }, 7302);
        }
        Integer progress = call.getInt("progress");
        try {
            BackgroundWorkService.update(getContext(), call.getString("title", "hibiki"), call.getString("text", ""), progress == null ? -1 : progress);
            call.resolve();
        } catch (RuntimeException refused) {
            // Started from the background where Android forbids it (12+): the work goes on while
            // the app is allowed to run.
            call.reject("Could not start background work: " + refused.getMessage());
        }
    }

    // --- Updating the app itself ----------------------------------------------------------------

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getContext().getPackageManager().canRequestPackageInstalls();
    }

    @PluginMethod
    public void canInstallPackages(PluginCall call) {
        JSObject result = new JSObject();
        result.put("granted", canInstallPackages());
        call.resolve(result);
    }

    /**
     * The system's "install unknown apps" switch for this app. Resolves with its state once the
     * person is back from the settings screen. (Some skins, MIUI among them, word the screen
     * differently; it is the same setting.)
     */
    @PluginMethod
    public void openInstallSettings(PluginCall call) {
        if (canInstallPackages()) {
            JSObject result = new JSObject();
            result.put("granted", true);
            call.resolve(result);
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
        startActivityForResult(call, intent, "installSettingsResult");
    }

    @ActivityCallback
    private void installSettingsResult(PluginCall call, ActivityResult result) {
        JSObject answer = new JSObject();
        answer.put("granted", canInstallPackages());
        call.resolve(answer);
    }

    /**
     * Whether the file is a newer build of this very app, signed with the same key - the check the
     * system does at install time too, made here first so the person is told why, and so a wrong
     * file is deleted instead of offered. `reason` is one of the codes core/updates.ts knows.
     */
    @PluginMethod
    public void verifyPackage(PluginCall call) {
        String path = call.getString("path");
        String version = call.getString("version");
        JSObject result = new JSObject();
        String reason = verifyPackageFile(path, version);
        result.put("ok", reason == null);
        if (reason != null) result.put("reason", reason);
        call.resolve(result);
    }

    @SuppressWarnings("deprecation")
    private String verifyPackageFile(String path, String version) {
        if (path == null || version == null) return "unreadable";
        PackageManager manager = getContext().getPackageManager();
        boolean modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
        int flags = modern ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive;
        PackageInfo installed;
        try {
            archive = manager.getPackageArchiveInfo(path, flags);
            if (archive == null) return "unreadable";
            installed = manager.getPackageInfo(getContext().getPackageName(), flags);
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            return "unreadable";
        }
        if (!getContext().getPackageName().equals(archive.packageName)) return "wrong-package";
        if (!version.equals(archive.versionName)) return "wrong-version";
        if (versionCodeOf(archive) <= versionCodeOf(installed)) return "not-newer";
        Set<String> offered = signersOf(archive, modern);
        if (offered.isEmpty() || !offered.equals(signersOf(installed, modern))) return "signature-mismatch";
        return null;
    }

    @SuppressWarnings("deprecation")
    private static long versionCodeOf(PackageInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signersOf(PackageInfo info, boolean modern) {
        Signature[] signatures = modern
            ? (info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners())
            : info.signatures;
        Set<String> hashes = new HashSet<>();
        if (signatures == null) return hashes;
        try {
            for (Signature signature : signatures) {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
                StringBuilder hex = new StringBuilder();
                for (byte b : digest) hex.append(String.format("%02x", b));
                hashes.add(hex.toString());
            }
        } catch (NoSuchAlgorithmException impossible) {
            return new HashSet<>();
        }
        return hashes;
    }

    /** Opens the system installer on a package verifyPackage accepted; answers once it is up. */
    @PluginMethod
    public void installPackage(PluginCall call) {
        String path = call.getString("path");
        if (path == null) {
            call.reject("No path");
            return;
        }
        try {
            File file = new File(path);
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (IllegalArgumentException | ActivityNotFoundException error) {
            call.reject("Could not open the installer: " + error.getMessage());
        }
    }
}

