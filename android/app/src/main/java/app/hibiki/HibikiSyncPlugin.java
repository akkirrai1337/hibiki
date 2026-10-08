package app.hibiki;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;

/**
 * Device sync's transport on the phone (core/sync/protocol.ts): one framed request to the computer
 * over a plain socket, and the discovery broadcast. Plain sockets rather than HTTP: Android refuses
 * cleartext HTTP to arbitrary local addresses, and the payload is encrypted end to end anyway.
 */
@CapacitorPlugin(name = "HibikiSync")
public class HibikiSyncPlugin extends Plugin {

    private static final int MAX_MESSAGE_BYTES = 64 * 1024 * 1024;

    @PluginMethod
    public void request(PluginCall call) {
        String host = call.getString("host");
        int port = call.getInt("port", 0);
        String message = call.getString("message", "");
        int timeout = call.getInt("timeoutMs", 20000);
        Http.io.execute(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), Math.min(timeout, 5000));
                socket.setSoTimeout(timeout);
                byte[] body = message.getBytes(StandardCharsets.UTF_8);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.writeInt(body.length);
                out.write(body);
                out.flush();
                DataInputStream in = new DataInputStream(socket.getInputStream());
                int length = in.readInt();
                if (length < 0 || length > MAX_MESSAGE_BYTES) throw new IllegalStateException("answer too large");
                byte[] answer = new byte[length];
                in.readFully(answer);
                JSObject result = new JSObject();
                result.put("message", new String(answer, StandardCharsets.UTF_8));
                call.resolve(result);
            } catch (Exception error) {
                call.reject(error.getMessage() == null ? error.toString() : error.getMessage());
            }
        });
    }

    /** Broadcasts the probe and collects the computers that answer within the time. */
    @PluginMethod
    public void discover(PluginCall call) {
        int timeout = call.getInt("timeoutMs", 1500);
        int port = call.getInt("port", 47653);
        String probe = call.getString("probe", "");
        Http.io.execute(() -> {
            JSArray found = new JSArray();
            Set<String> seen = new HashSet<>();
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                byte[] data = probe.getBytes(StandardCharsets.UTF_8);
                for (InetAddress target : broadcastAddresses()) {
                    try {
                        socket.send(new DatagramPacket(data, data.length, target, port));
                    } catch (Exception ignored) {
                        // One unusable address does not stop the others.
                    }
                }
                long deadline = System.currentTimeMillis() + timeout;
                byte[] buffer = new byte[4096];
                while (true) {
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0) break;
                    socket.setSoTimeout((int) left);
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(packet);
                    } catch (SocketTimeoutException done) {
                        break;
                    }
                    try {
                        JSONObject answer = new JSONObject(new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8));
                        if (!"hibiki".equals(answer.optString("app"))) continue;
                        String id = answer.optString("deviceId");
                        if (id.isEmpty() || !seen.add(id)) continue;
                        JSObject device = new JSObject();
                        device.put("deviceId", id);
                        device.put("name", answer.optString("name", "hibiki"));
                        device.put("host", packet.getAddress().getHostAddress());
                        device.put("port", answer.optInt("port", 47652));
                        device.put("kind", answer.optString("kind", "computer"));
                        found.put(device);
                    } catch (Exception ignored) {
                        // Not ours.
                    }
                }
            } catch (Exception error) {
                call.reject(error.getMessage() == null ? error.toString() : error.getMessage());
                return;
            }
            JSObject result = new JSObject();
            result.put("devices", found);
            call.resolve(result);
        });
    }

    /** The Wi-Fi network's own broadcast address (some routers drop 255.255.255.255), then the general one. */
    private Set<InetAddress> broadcastAddresses() {
        Set<InetAddress> out = new java.util.LinkedHashSet<>();
        try {
            WifiManager wifi = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            @SuppressWarnings("deprecation")
            DhcpInfo dhcp = wifi != null ? wifi.getDhcpInfo() : null;
            if (dhcp != null && dhcp.ipAddress != 0) {
                int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
                byte[] quads = new byte[4];
                for (int k = 0; k < 4; k++) quads[k] = (byte) ((broadcast >> (k * 8)) & 0xFF);
                out.add(InetAddress.getByAddress(quads));
            }
        } catch (Exception ignored) {
            // Falls back to the general broadcast below.
        }
        try {
            out.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) {
            // Cannot happen for a literal.
        }
        return out;
    }

    /** A line for the app log (core/logger.ts), which the page writes; logcat gets it too. */
    private void report(String level, String message) {
        if ("warn".equals(level) || "error".equals(level)) android.util.Log.w("HibikiSync", message);
        JSObject event = new JSObject();
        event.put("level", level);
        event.put("message", message);
        notifyListeners("log", event);
    }

    @PluginMethod
    public void deviceName(PluginCall call) {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String model = Build.MODEL == null ? "Android" : Build.MODEL;
        String name = model.toLowerCase().startsWith(manufacturer.toLowerCase()) ? model : (manufacturer + " " + model).trim();
        JSObject result = new JSObject();
        result.put("name", name.isEmpty() ? "Android" : name);
        call.resolve(result);
    }

    // --- Waiting for other devices (while the app is on screen) -----------------------------------

    private volatile java.net.ServerSocket server;
    private volatile DatagramSocket discoverySocket;
    private WifiManager.MulticastLock multicastLock;
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<String>> pendingAnswers = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong nextRequestId = new java.util.concurrent.atomic.AtomicLong();

    /**
     * Listens like the computer does (main/sync.ts): requests on `port`, the discovery probe on
     * `discoveryPort`, answered with `answer`. Each request goes to the page as a "request" event and
     * waits for its answer through respond(). Stopped when the app leaves the screen - Android would
     * not keep it running anyway.
     */
    @PluginMethod
    public void startServer(PluginCall call) {
        int port = call.getInt("port", 47652);
        int discoveryPort = call.getInt("discoveryPort", 47653);
        String probe = call.getString("probe", "");
        String answer = call.getString("answer", "");
        if (server != null) {
            call.resolve();
            return;
        }
        try {
            java.net.ServerSocket socket = new java.net.ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));
            server = socket;
        } catch (Exception error) {
            report("warn", "could not listen on port " + port + ": " + error.getMessage());
            call.reject("Could not listen: " + error.getMessage());
            return;
        }
        Thread accepter = new Thread(() -> {
            java.net.ServerSocket own = server;
            while (own != null && !own.isClosed()) {
                try {
                    Socket client = own.accept();
                    Http.io.execute(() -> serve(client));
                } catch (Exception closed) {
                    if (!own.isClosed()) report("warn", "stopped accepting connections: " + closed.getMessage());
                    break;
                }
            }
        }, "hibiki-sync-server");
        accepter.setDaemon(true);
        accepter.start();

        try {
            WifiManager wifi = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("hibiki-sync");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
            DatagramSocket udp = new DatagramSocket(null);
            udp.setReuseAddress(true);
            udp.setBroadcast(true);
            udp.bind(new InetSocketAddress(discoveryPort));
            discoverySocket = udp;
            Thread responder = new Thread(() -> {
                byte[] buffer = new byte[2048];
                byte[] reply = answer.getBytes(StandardCharsets.UTF_8);
                while (!udp.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udp.receive(packet);
                        String text = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                        if (!text.equals(probe)) continue;
                        report("debug", "discovery probe from " + packet.getAddress().getHostAddress() + ":" + packet.getPort());
                        udp.send(new DatagramPacket(reply, reply.length, packet.getAddress(), packet.getPort()));
                    } catch (Exception closed) {
                        if (udp.isClosed()) break;
                    }
                }
            }, "hibiki-sync-discovery");
            responder.setDaemon(true);
            responder.start();
        } catch (Exception error) {
            // Found by address only, then - requests still work.
            report("warn", "discovery not started (found by its last address only): " + error.getMessage());
        }
        call.resolve();
    }

    private void serve(Socket client) {
        String from = client.getInetAddress() == null ? "?" : client.getInetAddress().getHostAddress();
        try (Socket socket = client) {
            socket.setSoTimeout(60000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            int length = in.readInt();
            if (length < 0 || length > MAX_MESSAGE_BYTES) {
                report("warn", "request from " + from + " too large (" + length + " bytes), dropped");
                return;
            }
            byte[] body = new byte[length];
            in.readFully(body);
            String id = "s" + nextRequestId.incrementAndGet();
            java.util.concurrent.CompletableFuture<String> future = new java.util.concurrent.CompletableFuture<>();
            pendingAnswers.put(id, future);
            JSObject event = new JSObject();
            event.put("id", id);
            event.put("message", new String(body, StandardCharsets.UTF_8));
            event.put("address", socket.getInetAddress().getHostAddress());
            notifyListeners("request", event);
            String answer;
            try {
                answer = future.get(60, java.util.concurrent.TimeUnit.SECONDS);
            } finally {
                pendingAnswers.remove(id);
            }
            byte[] reply = answer.getBytes(StandardCharsets.UTF_8);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(reply.length);
            out.write(reply);
            out.flush();
        } catch (java.util.concurrent.TimeoutException error) {
            report("warn", "request from " + from + " not answered by the page within 60s");
        } catch (Exception error) {
            // A dropped or timed-out connection: the other device retries on its next sync.
            report("debug", "connection from " + from + ": " + error);
        }
    }

    /** The page's answer to a "request" event. */
    @PluginMethod
    public void respond(PluginCall call) {
        java.util.concurrent.CompletableFuture<String> future = pendingAnswers.get(call.getString("id", ""));
        if (future != null) future.complete(call.getString("message", ""));
        call.resolve();
    }

    @PluginMethod
    public void stopServer(PluginCall call) {
        stopServing();
        call.resolve();
    }

    private void stopServing() {
        java.net.ServerSocket own = server;
        server = null;
        if (own != null) {
            try {
                own.close();
            } catch (Exception ignored) {
                // Closing.
            }
        }
        DatagramSocket udp = discoverySocket;
        discoverySocket = null;
        if (udp != null) udp.close();
        if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        multicastLock = null;
    }

    @Override
    protected void handleOnDestroy() {
        stopServing();
        super.handleOnDestroy();
    }
}
