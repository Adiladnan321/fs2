package com.example.surfacelab;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.LinearLayout;
import com.example.labui.LabUi;

public final class ForensicsActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if(!BuildConfig.VULNERABLE) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root=LabUi.page(this,"LAB 05 / FORENSIC ARTIFACTS","Follow the evidence.","A reproducible, synthetic account leaves files and system artifacts.");
        LinearLayout c=LabUi.card(this,root,"SYNTHETIC CUSTOMER RECORD","Avery / account 8001","Account: DEMO-0042-8001\nInvoice: INV-1042\nBalance: 8,240 demo credits");
        LabUi.code(this,c,BuildConfig.VULNERABLE?"Recent-app capture: not protected (device dependent)":"Recent-app capture: FLAG_SECURE enabled");
        c=LabUi.card(this,root,"FILESYSTEM INVENTORY","Artifacts to collect","Use the guide's debug run-as collection to acquire only this lab's sandbox. A forensic filesystem acquisition is a separate, privileged capability.");
        LabUi.code(this,c,"Session\n"+LabStore.session(this)+"\n\nInvoice\n"+LabStore.invoice(this)+"\n\nLedger\n"+(BuildConfig.VULNERABLE?getDatabasePath("ledger.db"):new java.io.File(getNoBackupFilesDir(),"ledger.db"))+"\nInclude any -wal and -shm siblings.");
        c=LabUi.card(this,root,"BACKUP & CAPTURE","Know the acquisition limits",BuildConfig.VULNERABLE?
            "Backup rules include synthetic evidence. Legacy adb backup support varies. Android 12+ excludes target-31+ app data unless the app opts in through debuggable. This debug lab does; device tools may still omit it.":
            "Backup is disabled, both rule formats exclude the data, and sensitive fixtures live in noBackupFilesDir. Debug run-as still works because these comparison APKs are debug builds.");
        LabUi.code(this,c,"/data/system_ce/0/snapshots is system-owned.\nOrdinary adb shell and the companion app cannot read it.");
        LabUi.button(this,root,"Mark evidence inventory observed",()->{LabStore.record(this,5,"FLAG{forensic_artifacts_identified}"); new android.app.AlertDialog.Builder(this).setMessage("Evidence inventory observed. Use the collection script for actual files.").setPositiveButton("Done",null).show();});
    }
}
