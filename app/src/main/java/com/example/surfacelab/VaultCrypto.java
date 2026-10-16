package com.example.surfacelab;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.io.File;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class VaultCrypto {
    private static final String ALIAS="surfacelab_vault_wrapping_key";
    private static final String CONTENT="FLAG{biometric_callback_is_not_crypto_auth}\n\nConfidential training record\nAccount DEMO-0042-8001\nRecovery phrase: amber river training only";
    /** INTENTIONALLY VULNERABLE: an unwrapped, exportable key lives in a static singleton. */
    public static final class KeyCache { public static byte[] masterKey; private KeyCache() {} }
    private VaultCrypto() {}
    private static File fixture(Context c) { return new File(LabStore.evidenceRoot(c),"vault.json"); }
    private static KeyStore store() throws Exception { KeyStore s=KeyStore.getInstance("AndroidKeyStore"); s.load(null); return s; }
    private static String b64(byte[] b) { return Base64.encodeToString(b,Base64.NO_WRAP); }
    private static byte[] bytes(JSONObject j,String k) throws Exception { return Base64.decode(j.getString(k),Base64.DEFAULT); }
    public static synchronized void ensureFixture(Context c) throws Exception {
        KeyStore s=store();
        if(fixture(c).exists() && s.containsAlias(ALIAS)) return;
        if(BuildConfig.VULNERABLE) {
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false).build());
            gen.generateKey();
        } else {
            KeyPairGenerator gen=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
            KeyGenParameterSpec.Builder spec=new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1).setUserAuthenticationRequired(true);
            if(Build.VERSION.SDK_INT>=30) spec.setUserAuthenticationParameters(0,KeyProperties.AUTH_BIOMETRIC_STRONG);
            else spec.setUserAuthenticationValidityDurationSeconds(-1);
            gen.initialize(spec.build()); gen.generateKeyPair();
        }
        byte[] key=new byte[32]; new SecureRandom().nextBytes(key);
        try {
            Cipher enc=Cipher.getInstance("AES/GCM/NoPadding"); enc.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"));
            byte[] ciphertext=enc.doFinal(CONTENT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            JSONObject j=new JSONObject().put("iv",b64(enc.getIV())).put("ciphertext",b64(ciphertext));
            Cipher wrap;
            if(BuildConfig.VULNERABLE) {
                wrap=Cipher.getInstance("AES/GCM/NoPadding"); wrap.init(Cipher.ENCRYPT_MODE,s.getKey(ALIAS,null));
                j.put("wrap_iv",b64(wrap.getIV()));
            } else {
                // Reconstitute an unrestricted public key; only private-key decryption requires authentication.
                java.security.PublicKey pub=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(s.getCertificate(ALIAS).getPublicKey().getEncoded()));
                wrap=Cipher.getInstance("RSA/ECB/PKCS1Padding"); wrap.init(Cipher.ENCRYPT_MODE,pub);
            }
            j.put("wrapped_key",b64(wrap.doFinal(key))); LabStore.write(fixture(c),j.toString(2));
        } finally { Arrays.fill(key,(byte)0); }
    }
    public static byte[] unwrapKey(Context c) throws Exception {
        if(!BuildConfig.VULNERABLE) throw new SecurityException("Use the authenticated CryptoObject");
        if(KeyCache.masterKey==null) {
            JSONObject j=new JSONObject(LabStore.read(fixture(c)));
            Cipher wrap=Cipher.getInstance("AES/GCM/NoPadding");
            wrap.init(Cipher.DECRYPT_MODE,store().getKey(ALIAS,null),new GCMParameterSpec(128,bytes(j,"wrap_iv")));
            KeyCache.masterKey=wrap.doFinal(bytes(j,"wrapped_key"));
        }
        return KeyCache.masterKey;
    }
    public static Cipher prepareAuthenticatedCipher(Context c) throws Exception {
        ensureFixture(c);
        Cipher cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding"); cipher.init(Cipher.DECRYPT_MODE,store().getKey(ALIAS,null)); return cipher;
    }
    public static String unlock(Context c,Cipher authorized) throws Exception {
        JSONObject j=new JSONObject(LabStore.read(fixture(c)));
        byte[] key=BuildConfig.VULNERABLE?unwrapKey(c):authorized.doFinal(bytes(j,"wrapped_key"));
        try {
            Cipher dec=Cipher.getInstance("AES/GCM/NoPadding"); dec.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,bytes(j,"iv")));
            return new String(dec.doFinal(bytes(j,"ciphertext")),java.nio.charset.StandardCharsets.UTF_8);
        } finally { if(!BuildConfig.VULNERABLE) Arrays.fill(key,(byte)0); }
    }
}
