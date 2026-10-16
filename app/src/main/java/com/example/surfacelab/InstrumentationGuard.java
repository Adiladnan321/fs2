package com.example.surfacelab;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

/** Deliberately centralized and hookable. It examines this app's real memory mappings. */
public final class InstrumentationGuard {
    private InstrumentationGuard() {}

    static boolean isInstrumentationMapping(String mapping) {
        String value = mapping.toLowerCase(Locale.ROOT);
        return value.contains("frida-agent") || value.contains("frida-gadget")
            || value.contains("libgadget.so") || value.contains("/memfd:frida");
    }

    public static boolean isInstrumentationPresent() throws IOException {
        try (BufferedReader maps = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = maps.readLine()) != null) {
                if (isInstrumentationMapping(line)) return true;
            }
        }
        return false;
    }
}
