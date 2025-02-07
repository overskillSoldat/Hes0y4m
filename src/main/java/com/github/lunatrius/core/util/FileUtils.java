package com.github.lunatrius.core.util;

import com.github.lunatrius.core.reference.Reference;

import java.io.File;
import java.io.IOException;

public class FileUtils {
    public static String humanReadableByteCount(final long bytes) {
        final int unit = 1024;
        if (bytes < unit) {
            return bytes + " B";
        }

        final int exp = (int) (Math.log(bytes) / Math.log(unit));
        final String pre = "KMGTPE".charAt(exp - 1) + "i";

        return String.format("%3.0f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static boolean contains(final File root, final String filename) {
        return contains(root, new File(root, filename));
    }

    public static boolean contains(final File root, final File file) {
        try {
            return file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator);
        } catch (final IOException e) {
            System.out.println( e);
        }

        return false;
    }
}
