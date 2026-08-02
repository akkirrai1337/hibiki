package org.akkirrai.wasmtime;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;

public final class WasmtimeRuntimeSmokeActivity extends Activity {
    static {
        System.loadLibrary("wasmtime_spike_v2");
    }

    private static native int probe();

    private static native String protocolProbe(String request);

    private static native String protocolModuleProbe(byte[] module, String request);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int result = probe();
        String protocolResponse = protocolProbe("{\"requestId\":\"android-probe-1\",\"operation\":\"SEARCH\",\"payload\":{\"query\":\"frieren\",\"limit\":20,\"offset\":0,\"sort\":\"RELEVANCE\",\"typeAliases\":[],\"statusAliases\":[],\"includedGenreAliases\":[],\"excludedGenreAliases\":[],\"yearFrom\":null,\"yearTo\":null},\"protocolVersion\":1}");
        byte[] suppliedModule = ("(module "
                + "(import \"host\" \"call\" (func $host_call (param i32 i32) (result i64))) "
                + "(memory (export \"memory\") 2) "
                + "(global $heap (mut i32) (i32.const 4096)) "
                + "(func (export \"beakokit_reset\") i32.const 4096 global.set $heap) "
                + "(func (export \"beakokit_alloc\") (param i32) (result i32) "
                + "global.get $heap global.get $heap local.get 0 i32.add global.set $heap) "
                + "(func (export \"beakokit_call\") (param i32 i32) (result i64) "
                + "local.get 0 local.get 1 call $host_call)"
                + ")").getBytes(StandardCharsets.UTF_8);
        String moduleResponse = protocolModuleProbe(suppliedModule, "{\"requestId\":\"android-module-probe-1\",\"operation\":\"SEARCH\",\"payload\":{\"query\":\"frieren\",\"limit\":20,\"offset\":0,\"sort\":\"RELEVANCE\",\"typeAliases\":[],\"statusAliases\":[],\"includedGenreAliases\":[],\"excludedGenreAliases\":[],\"yearFrom\":null,\"yearTo\":null},\"protocolVersion\":1}");
        boolean protocolOk = protocolResponse != null
                && protocolResponse.contains("\"requestId\":\"android-probe-1\"")
                && protocolResponse.contains("\"payload\":{\"items\":[]}")
                && protocolResponse.contains("\"errorCode\":null");
        boolean moduleProbeOk = moduleResponse != null
                && moduleResponse.contains("\"requestId\":\"android-module-probe-1\"")
                && moduleResponse.contains("\"errorCode\":null");
        TextView view = new TextView(this);
        view.setText(result == 0 && protocolOk && moduleProbeOk
                ? "Wasmtime JNI probe: OK\nProtocol JNI bridge: OK\nModule JNI bridge: OK"
                : "Wasmtime JNI probe: FAILED");
        view.setTextSize(20.0f);
        setContentView(view);
    }
}
