package com.example.surfacelab;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import com.example.labui.LabUi;

public final class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) { super.onCreate(b); render(); }
    @Override protected void onResume() { super.onResume(); render(); }
    private void open(Class<?> c) { startActivity(new Intent(this,c)); }
    private void render() {
        int done=0; for(int i=1;i<=5;i++) if(LabStore.progress(this).getBoolean("lab_"+i,false)) done++;
        LinearLayout root=LabUi.page(this,"SURFACELAB     /     MOBILE SECURITY","Explore the\nattack surface.","Five hands-on labs in Android IPC, runtime trust, and forensic evidence.");
        LinearLayout status=LabUi.card(this,root,BuildConfig.VULNERABLE ? "VULNERABLE BUILD" : "HARDENED COMPARISON",done+" / 5 labs observed","API "+android.os.Build.VERSION.SDK_INT+"  ·  Target 35  ·  Synthetic data only");
        LabUi.code(this,status,getPackageName());
        LinearLayout c=LabUi.card(this,root,"01  /  DEEP LINKS","A link becomes authority","Untrusted routes can record a transfer or open a WebView with a session bridge.");
        LabUi.button(this,c,"Open local WebView demo",()->startActivity(new Intent(this,DeepLinkActivity.class).setData(android.net.Uri.parse((BuildConfig.VULNERABLE?"myapp":"safelab")+"://webview"))));
        c=LabUi.card(this,root,"02  /  FILEPROVIDER","The overbroad file grant","An exported sharing broker exposes private files through a non-exported provider.");
        LabUi.button(this,c,"Inspect sharing boundary",()->new android.app.AlertDialog.Builder(this).setTitle("Use SurfaceLab Probe")
            .setMessage("Open the companion app and request session.json. The vulnerable broker returns a read grant; the hardened broker rejects it.\n\nProvider authority: "+getPackageName()+".files").setPositiveButton("Got it",null).show());
        c=LabUi.card(this,root,"03  /  BIOMETRICS & KEYSTORE","A vault with a UI-only lock","Compare a hookable biometric callback with a cryptographic authorization gate.");
        LabUi.button(this,c,"Open confidential vault",()->open(VaultActivity.class));
        c=LabUi.card(this,root,"04  /  PENDINGINTENT","A ticket with too much power","The probe fills in a mutable ticket and reaches an unexported admin activity.");
        LabUi.button(this,c,"Inspect ticket boundary",()->new android.app.AlertDialog.Builder(this).setTitle("Use SurfaceLab Probe")
            .setMessage("Request a ticket in the probe, then tap Send modified ticket. A direct launch of AdminActivity remains denied by Android.\n\nOn API 34+, this training build explicitly opts into an unsafe implicit PendingIntent.")
            .setPositiveButton("Got it",null).show());
        c=LabUi.card(this,root,"05  /  FORENSIC ARTIFACTS","What the app leaves behind","Trace invoices, SQLite WAL files, backup rules, and recent-app snapshots.");
        LabUi.button(this,c,"Open evidence inventory",()->open(ForensicsActivity.class));
        LabUi.button(this,root,"Reset lab observations",()->{ LabStore.progress(this).edit().clear().apply(); render(); });
        LabUi.label(this,root,"TRAINING ENVIRONMENT\nAll accounts, keys, invoices, and flags belong to this local lab. Reset clears observations; clear app storage to regenerate fixtures.",12,LabUi.MUTED,false);
    }
}
