package com.example.surfacelab;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import androidx.core.content.FileProvider;
import java.util.ArrayList;

/** Device checks for actual fixture, grant-root and cryptographic behavior. No mocked Android APIs. */
public final class LabInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle b) { super.onCreate(b); start(); }
    private void check(boolean condition,String detail) { if(!condition) throw new AssertionError(detail); }
    private String text(android.view.View view) {
        StringBuilder result=new StringBuilder();
        if(view instanceof android.widget.TextView) result.append(((android.widget.TextView)view).getText()).append('\n');
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) result.append(text(group.getChildAt(i)));
        }
        return result.toString();
    }
    @Override public void onStart() {
        Bundle results=new Bundle(); ArrayList<String> checks=new ArrayList<>();
        try {
            Context c=getTargetContext();
            check(LabStore.read(LabStore.session(c)).contains("synthetic"),"Synthetic session fixture missing");checks.add("synthetic session fixture");
            check(LabStore.invoice(c).length()>100,"Invoice PDF missing");checks.add("invoice PDF generated");
            boolean backup=(c.getApplicationInfo().flags&ApplicationInfo.FLAG_ALLOW_BACKUP)!=0;
            check(backup==BuildConfig.VULNERABLE,"Backup manifest disagrees with flavor");checks.add("backup flavor policy");
            boolean grant=false;
            try { FileProvider.getUriForFile(c,c.getPackageName()+".files",LabStore.session(c));grant=true; }
            catch(IllegalArgumentException expected) { }
            check(grant==BuildConfig.VULNERABLE,"Provider root exposes the wrong scope");checks.add("provider root scope");
            if(BuildConfig.VULNERABLE) {
                check(VaultCrypto.unwrapKey(c).length==32,"Data key is not AES-256");
                check(VaultCrypto.unlock(c,null).contains("FLAG{biometric_callback_is_not_crypto_auth}"),"No-auth unlock did not decrypt vault");
                checks.add("vault actually decrypts without biometric crypto authorization");
            } else {
                boolean blocked=false;try {VaultCrypto.unwrapKey(c);}catch(SecurityException expected){blocked=true;}
                check(blocked,"Hardened build exposed an unwrapped data key");checks.add("hardened no-auth key access denied");
                check(LabStore.session(c).getParentFile().equals(c.getNoBackupFilesDir()),"Sensitive data not in noBackup");checks.add("sensitive fixture excluded from backup location");
            }
            VaultActivity vault=(VaultActivity)startActivitySync(new Intent(c,VaultActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try {
                runOnMainSync(()->new AuthCallback(vault).onAuthenticationSucceeded(null));
                final String[] screen={""};runOnMainSync(()->screen[0]=text(vault.getWindow().getDecorView()));
                check(BuildConfig.VULNERABLE?screen[0].contains("FLAG{biometric_callback_is_not_crypto_auth}"):screen[0].contains("Missing authenticated CryptoObject"),"Forged callback produced unexpected vault state: "+screen[0]);
                checks.add("forged biometric success "+(BuildConfig.VULNERABLE?"decrypts vulnerable vault":"is rejected by hardened vault"));
                boolean secure=(vault.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0;
                check(secure!=BuildConfig.VULNERABLE,"Vault screen capture policy disagrees with flavor");checks.add("vault FLAG_SECURE flavor policy");
            } finally {runOnMainSync(vault::finish);}
            results.putString("stream","\nPASS ("+checks.size()+" checks): "+String.join(", ",checks)+"\n");
            finish(Activity.RESULT_OK,results);
        } catch(Throwable e) {
            results.putString("stream","\nFAIL: "+e+"\n");finish(Activity.RESULT_CANCELED,results);
        }
    }
}
