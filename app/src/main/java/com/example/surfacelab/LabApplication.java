package com.example.surfacelab;

import android.app.Application;
import android.util.Log;

public final class LabApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        try { LabStore.seed(this); } catch (Exception e) { Log.e("SurfaceLab", "Synthetic fixture initialization failed", e); }
    }
}
