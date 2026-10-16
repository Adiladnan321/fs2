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
            check(!InstrumentationGuard.isInstrumentationPresent(), "Unexpected instrumentation in baseline process");
            checks.add("runtime guard scans actual uninstrumented process mappings");
            String runtimeReport=RuntimeSecret.readProtectedReport("instrumentation-fixture");
            java.util.regex.Matcher flag=java.util.regex.Pattern.compile("FLAG\\{runtime_hook_[0-9a-f]{32}\\}").matcher(runtimeReport);
            check(flag.find()==BuildConfig.VULNERABLE, "Runtime payload disagrees with flavor");
            if(BuildConfig.VULNERABLE) {
                check(RuntimePayload.acceptsFlag("instrumentation-fixture",flag.group()), "Recovered runtime flag rejected");
                check(!RuntimePayload.acceptsFlag("different-challenge",flag.group()), "Stale challenge flag accepted");
                check(!RuntimePayload.acceptsFlag("instrumentation-fixture","FLAG{wrong}"), "Invalid flag accepted");
            } else check(!RuntimePayload.acceptsFlag("instrumentation-fixture","FLAG{wrong}"), "Public payload accepted a secret flag");
            checks.add("runtime report flavor policy and challenge-specific flag validation");
            c.getSharedPreferences("lab_progress",Context.MODE_PRIVATE).edit().remove("lab_6").commit();
            RuntimeChallengeActivity runtime=(RuntimeChallengeActivity)startActivitySync(new Intent(c,RuntimeChallengeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try {
                final String[] screen={""};runOnMainSync(()->screen[0]=text(runtime.getWindow().getDecorView()));
                check(!screen[0].contains("FLAG{runtime_hook_"), "Runtime report leaked before operation");
                check(!LabStore.progress(c).getBoolean("lab_6",false), "Entering runtime challenge completed the lab");
                runOnMainSync(runtime::runProtectedOperation);
                runOnMainSync(()->screen[0]=text(runtime.getWindow().getDecorView()));
                check(!screen[0].contains("FLAG{runtime_hook_"), "Protected report leaked onto normal UI");
                if(BuildConfig.VULNERABLE) {
                    check(!LabStore.progress(c).getBoolean("lab_6",false), "Running report completed lab without flag proof");
                    runOnMainSync(()->runtime.verifyRecoveredFlag("FLAG{wrong}"));
                    check(!LabStore.progress(c).getBoolean("lab_6",false), "Wrong flag completed the runtime lab");
                    java.lang.reflect.Field challenge=RuntimeChallengeActivity.class.getDeclaredField("challenge");
                    challenge.setAccessible(true);
                    String report=RuntimeSecret.readProtectedReport((String)challenge.get(runtime));
                    java.util.regex.Matcher recovered=java.util.regex.Pattern.compile("FLAG\\{runtime_hook_[0-9a-f]{32}\\}").matcher(report);
                    check(recovered.find(), "Runtime report has no flag");
                    String answer=recovered.group();
                    runOnMainSync(()->runtime.verifyRecoveredFlag(answer));
                    check(LabStore.progress(c).getBoolean("lab_6",false), "Recovered runtime flag did not complete the lab");
                } else check(screen[0].contains("No Lab 6 secret"), "Hardened runtime comparison omitted public-only result");
                checks.add("runtime UI hides report and verifies actual recovered flag before completion");
                String observation=LabStore.read(new java.io.File(c.getFilesDir(),"runtime-challenge.json"));
                check(observation.contains(BuildConfig.VULNERABLE?"flag-recovered":"public-receipt-only") && !observation.contains("FLAG{"), "Runtime evidence leaked answer or lost status");
                checks.add("runtime observation records status without disclosing flag");
            } finally {runOnMainSync(runtime::finish);}
            results.putString("stream","\nPASS ("+checks.size()+" checks): "+String.join(", ",checks)+"\n");
            finish(Activity.RESULT_OK,results);
        } catch(Throwable e) {
            results.putString("stream","\nFAIL: "+e+"\n");finish(Activity.RESULT_CANCELED,results);
        }
    }
}
