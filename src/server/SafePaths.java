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

        // Reject Windows reserved device names and trailing dot/space
        String clean = relativePath.replace('\\', '/');
        for (String segment : clean.split("/")) {
            if (segment.isEmpty()) continue;
            if (!segment.equals(".") && !segment.equals("..")) {
                if (segment.endsWith(".") || segment.endsWith(" ")) {
                    throw new SecurityException("Windows filenames cannot end with dot or space: " + segment);
                }
            }
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

        // Step 1: Check every existing path component from normRoot down to resolved using NOFOLLOW_LINKS
        Path curr = normRoot;
        Path rel = normRoot.relativize(resolved);
        for (Path element : rel) {
            curr = curr.resolve(element);
            if (Files.exists(curr, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                if (isDanglingLink(curr)) {
                    throw new SecurityException("Dangling filesystem link detected: " + curr);
                }
                if (isLinkOrReparsePoint(curr)) {
                    throw new SecurityException("Filesystem links and junctions are forbidden in sandbox: " + curr);
                }
                try {
                    Path realCurr = curr.toRealPath();
                    if (!realCurr.startsWith(realRoot)) {
                        throw new SecurityException("Filesystem link points outside root boundary: " + curr);
                    }
                } catch (IOException e) {
                    throw new SecurityException("Cannot resolve real path: " + e.getMessage());
                }
            } else {
                // If this component does not exist, subsequent components cannot exist either
                break;
            }
        }

        // Step 2: Confinement check for non-existent target (e.g. STOR, MKD)
        if (!Files.exists(resolved, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            Path ancestor = resolved.getParent();
            while (ancestor != null && !Files.exists(ancestor, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                ancestor = ancestor.getParent();
            }
            if (ancestor != null) {
                if (isDanglingLink(ancestor) || isLinkOrReparsePoint(ancestor)) {
                    throw new SecurityException("Ancestor is a forbidden link or junction: " + ancestor);
                }
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

    public static boolean isLinkOrReparsePoint(Path p) {
        if (!Files.exists(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (Files.isSymbolicLink(p)) {
            return true;
        }
        try {
            java.nio.file.attribute.BasicFileAttributes attrs =
                    Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            return attrs.isSymbolicLink() || attrs.isOther();
        } catch (IOException e) {
            return true; // If attributes cannot be read safely, treat as potentially unsafe
        }
    }

    public static boolean isDanglingLink(Path p) {
        return Files.exists(p, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !Files.exists(p);
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
