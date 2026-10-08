package app.hibiki;

import android.content.res.Configuration;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(HibikiNetPlugin.class);
        registerPlugin(HibikiBrowserPlugin.class);
        registerPlugin(HibikiFilesPlugin.class);
        registerPlugin(HibikiDbPlugin.class);
        registerPlugin(HibikiSecurePlugin.class);
        registerPlugin(HibikiDownloadsPlugin.class);
        registerPlugin(HibikiResolverPlugin.class);
        registerPlugin(HibikiAppPlugin.class);
        registerPlugin(HibikiSyncPlugin.class);
        registerPlugin(app.hibiki.apk.HibikiApkPlugin.class);
        super.onCreate(savedInstanceState);
        // The page draws under the status and navigation bars and pads itself by the safe-area
        // insets (Capacitor's SystemBars passes them through for viewport-fit=cover). Only after
        // super.onCreate(): that is where BridgeActivity switches to its NoActionBar theme, and
        // touching the window before it builds the decor with the launch theme's action bar.
        EdgeToEdge.enable(this);
        // Capacitor builds its own client and starts loading during super.onCreate(). Swap in ours
        // and load again, so every request from the first page on goes through it.
        bridge.setWebViewClient(new HibikiWebViewClient(bridge));
        bridge.reload();
        // Back belongs to the page: it closes whatever sheet is open, steps back through the app's own
        // history, and only on the first screen asks HibikiApp to leave. Pages HibikiBrowser shows on
        // top register their own callbacks later, so those take Back first.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                bridge.triggerWindowJSEvent("hibikiback");
            }
        });
    }

    /** The player shows only its picture while in the small window (see HibikiAppPlugin's PiP). */
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (bridge != null) bridge.triggerWindowJSEvent("hibikipip", "{ \"active\": " + isInPictureInPictureMode + " }");
    }
}
