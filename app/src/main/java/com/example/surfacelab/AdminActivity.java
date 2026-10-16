package com.example.surfacelab;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import com.example.labui.LabUi;

public final class AdminActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root=LabUi.page(this,"LAB 04 / UNEXPORTED ACTIVITY","Admin route reached.","Android used the PendingIntent creator identity to launch this private component.");
        if(!BuildConfig.VULNERABLE) { LabUi.code(this,root,"BLOCKED: this route has no external authorization."); return; }
        try {
            String location=LabStore.exportAudit(this);
            LabStore.record(this,4,"FLAG{mutable_ticket_admin_execution}");
            LabUi.code(this,root,"FLAG{mutable_ticket_admin_execution}\n\nSynthetic audit exported to:\n"+location);
            LabUi.code(this,root,"Process UID: "+android.os.Process.myUid());
        } catch(Exception e) { LabUi.code(this,root,"Export failed: "+e.getMessage()); }
        LabUi.button(this,root,"Return to probe",this::finish);
    }
}
