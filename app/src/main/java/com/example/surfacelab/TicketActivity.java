package com.example.surfacelab;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public final class TicketActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            Intent base=BuildConfig.VULNERABLE?new Intent():new Intent(this,ReceiptActivity.class);
            int flags=PendingIntent.FLAG_CANCEL_CURRENT;
            if(BuildConfig.VULNERABLE) {
                // Components are never filled unless the creator grants this additional permission.
                flags|=Intent.FILL_IN_COMPONENT;
                if(Build.VERSION.SDK_INT>=31) flags|=PendingIntent.FLAG_MUTABLE;
                // A deliberate opt-in is required for this exact anti-pattern at target 34+.
                if(Build.VERSION.SDK_INT>=34) flags|=PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT;
            } else flags|=PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi=PendingIntent.getActivity(this,1042,base,flags);
            setResult(RESULT_OK,new Intent().putExtra("ticket",pi));
        } catch(Exception e) { setResult(RESULT_CANCELED,new Intent().putExtra("error",e.toString())); }
        finish();
    }
}
