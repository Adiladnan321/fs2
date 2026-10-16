package com.example.surfacelab;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Synthetic per-challenge content. The digest is a training marker, not encryption. */
final class RuntimePayload {
    private RuntimePayload() {}

    private static String flag(String challenge) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(("SurfaceLab/runtime/training-only/" + challenge).getBytes(StandardCharsets.UTF_8));
        StringBuilder value = new StringBuilder("FLAG{runtime_hook_");
        char[] hex = "0123456789abcdef".toCharArray();
        for (int i = 0; i < 16; i++) {
            value.append(hex[(digest[i] & 255) >>> 4]).append(hex[digest[i] & 15]);
        }
        return value.append('}').toString();
    }

    static String createReport(String challenge) throws Exception {
        return "SurfaceLab / synthetic runtime report\nReference: RT-006\n"
            + "This report exists inside the training app's process.\n" + flag(challenge);
    }

    static boolean acceptsFlag(String challenge, String candidate) throws Exception {
        return candidate != null && MessageDigest.isEqual(flag(challenge).getBytes(StandardCharsets.UTF_8),
            candidate.trim().getBytes(StandardCharsets.UTF_8));
    }
}
