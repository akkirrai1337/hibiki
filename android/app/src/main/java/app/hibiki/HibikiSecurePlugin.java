package app.hibiki;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * SecureStorePort for Android: AES-GCM with a key that never leaves the Android Keystore - the
 * counterpart of Electron's safeStorage. Ciphertext is base64(iv || encrypted bytes).
 */
@CapacitorPlugin(name = "HibikiSecure")
public class HibikiSecurePlugin extends Plugin {

    private static final String ALIAS = "hibiki-secure-store";
    private static final int IV_BYTES = 12;

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) store.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build());
        return generator.generateKey();
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject result = new JSObject();
        try {
            key();
            result.put("value", true);
        } catch (Exception e) {
            result.put("value", false);
        }
        call.resolve(result);
    }

    @PluginMethod
    public void encrypt(PluginCall call) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(call.getString("value", "").getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            JSObject result = new JSObject();
            result.put("value", Base64.encodeToString(out, Base64.NO_WRAP));
            call.resolve(result);
        } catch (Exception e) {
            call.reject("encrypt failed: " + e, e);
        }
    }

    @PluginMethod
    public void decrypt(PluginCall call) {
        try {
            byte[] data = Base64.decode(call.getString("value", ""), Base64.DEFAULT);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, data, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
            JSObject result = new JSObject();
            result.put("value", new String(plain, StandardCharsets.UTF_8));
            call.resolve(result);
        } catch (Exception e) {
            call.reject("decrypt failed: " + e, e);
        }
    }
}
