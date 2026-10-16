package com.example.surfacelab;

import android.app.Activity;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.labui.LabUi;
import javax.crypto.Cipher;

public final class VaultActivity extends Activity {
    private TextView result;
    private CancellationSignal cancellation;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if(!BuildConfig.VULNERABLE) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root=LabUi.page(this,"LAB 03 / BIOMETRICS & KEYSTORE","The confidential vault.",BuildConfig.VULNERABLE?
            "A biometric callback gates the UI. The wrapping key needs no user authentication.":
            "The private wrapping key requires a fresh strong biometric operation through a CryptoObject.");
        LinearLayout c=LabUi.card(this,root,"ENCRYPTED TRAINING RECORD","Locked","Enroll a fingerprint in emulator Settings to use the real biometric prompt. The Frida lab can demonstrate the vulnerable callback without enrollment.");
        LabUi.button(this,c,"Authenticate to unlock",this::beginAuthentication);
        result=LabUi.code(this,c,"Vault is locked.");
    }
    public void beginAuthentication() {
        try {
            if(cancellation!=null) cancellation.cancel(); cancellation=new CancellationSignal();
            BiometricPrompt prompt=new BiometricPrompt.Builder(this).setTitle("Unlock SurfaceLab vault")
                .setSubtitle("Synthetic training record").setNegativeButton("Cancel",getMainExecutor(),(d,w)->{}).build();
            if(BuildConfig.VULNERABLE) prompt.authenticate(cancellation,getMainExecutor(),new AuthCallback(this));
            else {
                Cipher cipher=VaultCrypto.prepareAuthenticatedCipher(this);
                prompt.authenticate(new BiometricPrompt.CryptoObject(cipher),cancellation,getMainExecutor(),new AuthCallback(this));
            }
        } catch(Exception e) { showMessage("Authentication unavailable: "+e.getMessage()+"\nEnroll a strong biometric before using the hardened vault."); }
    }
    public void unlockFromCallback(BiometricPrompt.AuthenticationResult auth) {
        try {
            Cipher cipher=null;
            if(!BuildConfig.VULNERABLE) {
                if(auth==null || auth.getCryptoObject()==null || auth.getCryptoObject().getCipher()==null) throw new SecurityException("Missing authenticated CryptoObject");
                cipher=auth.getCryptoObject().getCipher();
            }
            String record=VaultCrypto.unlock(this,cipher); result.setText(record);
            LabStore.record(this,3,BuildConfig.VULNERABLE?"FLAG{biometric_callback_is_not_crypto_auth}":"CryptoObject-authorized vault unlock");
        } catch(Exception e) { showMessage("BLOCKED: "+e.getClass().getSimpleName()+": "+e.getMessage()); }
    }
    public void showMessage(String message) { result.setText(message); }
    @Override protected void onDestroy() { if(cancellation!=null)cancellation.cancel(); super.onDestroy(); }
}
