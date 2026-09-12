package com.n4monitor.common;

public final class UnitUtils {

    private UnitUtils() {
    }

    /**
     * Normalizes engineering units, repairing known character encoding corruptions
     * such as degree Celsius corrupted into \u64B8C (GBK misinterpretation of UTF-8 degree sign),
     * ?C, or \uFFFDC.
     */
    public static String normalizeUnit(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        boolean malformedDegree = normalized.contains("?")
                || normalized.contains("\uFFFD")
                || normalized.contains("\u00B0")
                || normalized.contains("\u64B8");
        if (malformedDegree && (normalized.endsWith("C") || normalized.endsWith("c"))) {
            return "\u00B0C";
        }
        return normalized;
    }
}
