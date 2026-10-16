package server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Centralized, safe filesystem resolution helper.
 * Enforces real-path boundary confinement against:
 * - String path traversal (.., encoded dots)
 * - Windows NTFS Junctions and Symbolic Links pointing outside the designated root
 * - Windows device names (CON, PRN, AUX, NUL, COM1-9, LPT1-9)
 * - Windows Alternate Data Streams (ADS, ":")
 * - Drive letters and UNC paths
 */
public final class SafePaths {

    private SafePaths() {}

    /**
     * Resolves and verifies that a relative path stays within the real boundary of root.
     * Works for both existing files and non-existent targets (by checking existing ancestors).
     */
    public static Path resolveSafe(Path root, String relativePath) {
        if (root == null) {
            throw new SecurityException("Root path cannot be null");
        }
        if (relativePath == null || relativePath.isEmpty()) {
            return getRealOrNormalized(root);
        }

        // Sanitize string characters
        if (relativePath.indexOf('\0') >= 0) {
            throw new SecurityException("Null byte injection detected");
        }
        if (relativePath.contains(":")) {
            throw new SecurityException("Alternate Data Streams or drive letters forbidden");
        }
        if (relativePath.startsWith("\\\\") || relativePath.startsWith("//")) {
            throw new SecurityException("UNC network paths forbidden");
        }

        // Reject Windows reserved device names
        String clean = relativePath.replace('\\', '/');
        for (String segment : clean.split("/")) {
            if (isReservedDeviceName(segment)) {
                throw new SecurityException("Windows reserved device name forbidden: " + segment);
            }
        }

        Path normRoot = root.toAbsolutePath().normalize();
        Path realRoot = getRealOrNormalized(normRoot);

        Path resolved = normRoot.resolve(clean).normalize().toAbsolutePath();
        if (!resolved.startsWith(normRoot)) {
            throw new SecurityException("Path traversal detected: outside root boundary");
        }

        // Real path confinement check (prevents NTFS Junction / Symlink escape)
        if (Files.exists(resolved)) {
            try {
                Path realResolved = resolved.toRealPath();
                if (!realResolved.startsWith(realRoot)) {
                    throw new SecurityException("Filesystem link / junction points outside root boundary");
                }
                return realResolved;
            } catch (IOException e) {
                throw new SecurityException("Cannot resolve real path: " + e.getMessage());
            }
        } else {
            // Target does not exist yet (e.g. STOR, MKD): verify lowest existing ancestor
            Path ancestor = resolved.getParent();
            while (ancestor != null && !Files.exists(ancestor)) {
                ancestor = ancestor.getParent();
            }
            if (ancestor != null) {
                try {
                    Path realAncestor = ancestor.toRealPath();
                    if (!realAncestor.startsWith(realRoot)) {
                        throw new SecurityException("Filesystem ancestor link points outside root boundary");
                    }
                } catch (IOException e) {
                    throw new SecurityException("Cannot resolve ancestor real path: " + e.getMessage());
                }
            }
        }

        return resolved;
    }

    private static Path getRealOrNormalized(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            return p.toAbsolutePath().normalize();
        }
    }

    private static boolean isReservedDeviceName(String name) {
        if (name == null || name.isEmpty()) return false;
        String base = name;
        int dot = base.indexOf('.');
        if (dot >= 0) {
            base = base.substring(0, dot);
        }
        String u = base.toUpperCase(Locale.ROOT).trim();
        return u.equals("CON") || u.equals("PRN") || u.equals("AUX") || u.equals("NUL")
                || u.equals("COM1") || u.equals("COM2") || u.equals("COM3") || u.equals("COM4")
                || u.equals("COM5") || u.equals("COM6") || u.equals("COM7") || u.equals("COM8") || u.equals("COM9")
                || u.equals("LPT1") || u.equals("LPT2") || u.equals("LPT3") || u.equals("LPT4")
                || u.equals("LPT5") || u.equals("LPT6") || u.equals("LPT7") || u.equals("LPT8") || u.equals("LPT9");
    }
}
