package org.akkirrai.wasmtime;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class WasmtimeRuntimeSmokeActivity extends Activity {
    static {
        System.loadLibrary("wasmtime_spike_v2");
    }

    private static native int probe();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int result = probe();
        TextView view = new TextView(this);
        view.setText(result == 0 ? "Wasmtime JNI probe: OK" : "Wasmtime JNI probe: FAILED");
        view.setTextSize(20.0f);
        setContentView(view);
    }
}
