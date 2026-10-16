package com.example.surfacelab;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.labui.LabUi;
import java.io.File;
import java.util.UUID;
import org.json.JSONObject;

public final class RuntimeChallengeActivity extends Activity {
    private String challenge;
    private boolean operationCompleted;
    private TextView result;
    private EditText recoveredFlag;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        challenge = state == null ? UUID.randomUUID().toString() : state.getString("challenge", UUID.randomUUID().toString());
        operationCompleted = state != null && state.getBoolean("operationCompleted");
        if (!allowExecution()) return;
        if (!BuildConfig.VULNERABLE) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root = LabUi.page(this, "LAB 06 / FRIDA & RUNTIME TRUST", "Hook the guard.\nFollow the return value.",
            "Two stages: keep the app running under instrumentation, then recover the flag from a protected operation.");
        LinearLayout card = LabUi.card(this, root, "STAGE 1 / THE GUARD", "Environment check passed",
            "The challenge checks for instrumentation when it opens and before reading a report. A detected tool closes SurfaceLab.");
        LabUi.label(this, card, "Passing this check alone does not complete the challenge.", 14, LabUi.MUTED, false);
        card = LabUi.card(this, root, "STAGE 2 / THE REPORT", BuildConfig.VULNERABLE ? "Recover the hidden flag" : "Public receipt only",
            BuildConfig.VULNERABLE
                ? "Run the protected operation, inspect its return value with your hook, then paste the recovered flag below. The normal screen never displays the report."
                : "This comparison ships no Lab 6 secret report. Bypassing the same guard reveals only a public receipt.");
        LabUi.button(this, card, "Run protected operation", this::runProtectedOperation);
        result = LabUi.code(this, card, operationCompleted ? "Operation already completed for this challenge." : "Ready. No protected report has been read.");
        if (BuildConfig.VULNERABLE) {
            recoveredFlag = new EditText(this);
            recoveredFlag.setHint("Paste the recovered FLAG{...}");
            recoveredFlag.setTextColor(LabUi.INK);
            recoveredFlag.setHintTextColor(LabUi.MUTED);
            recoveredFlag.setSingleLine(true);
            recoveredFlag.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            recoveredFlag.setContentDescription("Recovered runtime flag");
            card.addView(recoveredFlag, new LinearLayout.LayoutParams(-1, -2));
            LabUi.button(this, card, "Verify recovered flag", () -> verifyRecoveredFlag(recoveredFlag.getText().toString()));
        }
        LabUi.button(this, root, "Return to dashboard", this::finish);
    }

    private boolean allowExecution() {
        try {
            if (!InstrumentationGuard.isInstrumentationPresent()) return true;
            saveObservation("instrumentation-detected");
            Log.w("SurfaceLabRuntime", "INSTRUMENTATION DETECTED: closing SurfaceLab before protected report access");
            finishAndRemoveTask();
            android.os.Process.killProcess(android.os.Process.myPid());
        } catch (Exception error) {
            saveObservation("environment-check-unavailable");
            LinearLayout root = LabUi.page(this, "LAB 06 / ENVIRONMENT CHECK", "Check unavailable", "The report remains closed because the environment check could not run.");
            LabUi.code(this, root, error.getClass().getSimpleName() + ": " + error.getMessage());
            LabUi.button(this, root, "Return to dashboard", this::finish);
        }
        return false;
    }

    public void runProtectedOperation() {
        if (!allowExecution()) return;
        try {
            String report = RuntimeSecret.readProtectedReport(challenge);
            operationCompleted = true;
            result.setText(BuildConfig.VULNERABLE
                ? "Protected operation completed (" + report.length() + " characters).\nThe report is hidden from this screen. Recover its flag from the return value."
                : "Public receipt processed. No Lab 6 secret is present in this build.");
            saveObservation(BuildConfig.VULNERABLE ? "operation-completed" : "public-receipt-only");
            if (!BuildConfig.VULNERABLE) LabStore.record(this, 6, "Runtime comparison: public receipt only");
        } catch (Exception error) {
            result.setText("BLOCKED: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    public boolean verifyRecoveredFlag(String candidate) {
        try {
            if (!operationCompleted) {
                result.setText("Run the protected operation before submitting a recovered flag.");
                return false;
            }
            if (!RuntimePayload.acceptsFlag(challenge, candidate)) {
                result.setText("Flag not accepted. Use the value returned for this challenge.");
                return false;
            }
            LabStore.record(this, 6, "Runtime challenge: recovered flag verified");
            saveObservation("flag-recovered");
            result.setText("Flag verified. Both challenge stages are complete.");
            return true;
        } catch (Exception error) {
            result.setText("Verification failed: " + error.getMessage());
            return false;
        }
    }

    private void saveObservation(String status) {
        try {
            // Keep the answer out of debug evidence files so collection is not a shortcut.
            JSONObject observation = new JSONObject().put("status", status).put("synthetic", true)
                .put("package", getPackageName()).put("timestamp_ms", System.currentTimeMillis());
            LabStore.write(new File(getFilesDir(), "runtime-challenge.json"), observation.toString(2));
        } catch (Exception error) { Log.e("SurfaceLabRuntime", "Could not save challenge observation", error); }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("challenge", challenge);
        state.putBoolean("operationCompleted", operationCompleted);
    }
}
