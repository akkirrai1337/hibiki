package org.akkirrai.beakokit.runtime;

public final class NativeSourceRuntimeBridge {
    private NativeSourceRuntimeBridge() {}

    public interface Host {
        byte[] call(byte[] request);
    }

    public static native String protocolModuleCallWithHost(
            byte[] module,
            String request,
            Host host,
            long cancellationScopeId);
}
