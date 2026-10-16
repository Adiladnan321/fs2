package com.example.surfacelab;

/** No secret report or flag generator is shipped in this flavor's Lab 6 payload. */
final class RuntimePayload {
    private RuntimePayload() {}

    static String createReport(String challenge) {
        return "SurfaceLab / public runtime receipt\nReference: RT-006\n"
            + "No confidential report is supplied to this offline comparison.";
    }

    static boolean acceptsFlag(String challenge, String candidate) {
        return false;
    }
}
