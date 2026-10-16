package com.example.surfacelab;
import android.app.Activity;
import android.os.Bundle;
import com.example.labui.LabUi;
public final class ReceiptActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        android.widget.LinearLayout root=LabUi.page(this,"LAB 04 / HARDENED TICKET","The destination stayed fixed.","The explicit, immutable PendingIntent ignores the probe's supplied component and extras.");
        LabUi.code(this,root,"BLOCKED: AdminActivity was not launched.\nNo audit was exported.");
        LabUi.button(this,root,"Return to probe",this::finish);
    }
}
