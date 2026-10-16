package com.example.surfacelab;
import android.hardware.biometrics.BiometricPrompt;
public final class AuthCallback extends BiometricPrompt.AuthenticationCallback {
    private final VaultActivity owner;
    public AuthCallback(VaultActivity owner) { this.owner=owner; }
    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) { owner.unlockFromCallback(result); }
    @Override public void onAuthenticationError(int code,CharSequence message) { owner.showMessage("Biometric prompt: "+message); }
    @Override public void onAuthenticationFailed() { owner.showMessage("Fingerprint did not match. Try again."); }
}
