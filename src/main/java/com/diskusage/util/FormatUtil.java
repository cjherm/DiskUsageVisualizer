package com.diskusage.util;

public final class FormatUtil {

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB", "PB"};

    private FormatUtil() {
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        int unitIndex = 0;
        while (value >= 1024 && unitIndex < UNITS.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return String.format("%.2f %s", value, UNITS[unitIndex]);
    }

    public static String formatPercent(double fraction) {
        return String.format("%.1f%%", fraction * 100);
    }
}
