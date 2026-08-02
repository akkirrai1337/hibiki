package org.akkirrai.wasmtime;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class WasmtimeRuntimeSmokeActivity extends Activity {
    static {
        System.loadLibrary("wasmtime_spike_v2");
    }

    private static native int probe();

    private static native String protocolProbe(String request);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int result = probe();
        String protocolResponse = protocolProbe("{\"requestId\":\"android-probe-1\",\"operation\":\"SEARCH\",\"payload\":{\"query\":\"frieren\",\"limit\":20,\"offset\":0,\"sort\":\"RELEVANCE\",\"typeAliases\":[],\"statusAliases\":[],\"includedGenreAliases\":[],\"excludedGenreAliases\":[],\"yearFrom\":null,\"yearTo\":null},\"protocolVersion\":1}");
        boolean protocolOk = protocolResponse != null
                && protocolResponse.contains("\"requestId\":\"android-probe-1\"")
                && protocolResponse.contains("\"payload\":{\"items\":[]}")
                && protocolResponse.contains("\"errorCode\":null");
        TextView view = new TextView(this);
        view.setText(result == 0 && protocolOk
                ? "Wasmtime JNI probe: OK\nProtocol JNI bridge: OK"
                : "Wasmtime JNI probe: FAILED");
        view.setTextSize(20.0f);
        setContentView(view);
    }
}
