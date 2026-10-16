package com.example.surfacelab;

/** Stage two: hook this return value while the lab executes its protected operation. */
public final class RuntimeSecret {
    private RuntimeSecret() {}

    public static String readProtectedReport(String challenge) throws Exception {
        if (InstrumentationGuard.isInstrumentationPresent()) {
            throw new SecurityException("Instrumentation detected before report access");
        }
        return RuntimePayload.createReport(challenge);
    }
}
