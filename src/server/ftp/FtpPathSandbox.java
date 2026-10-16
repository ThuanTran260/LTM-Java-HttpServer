package server.ftp;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 4-Layer Chroot Sandbox and Path Traversal Defense.
 * Layer 1: Null byte and URL encoding sanitization.
 * Layer 2: Virtual path normalization and clamping at root /.
 * Layer 3: Physical path resolution stripping Windows drive letters.
 * Layer 4: Physical boundary confinement assertion (startsWith serverRoot).
 */
public class FtpPathSandbox {

    private final Path serverRoot;

    public FtpPathSandbox(Path serverRoot) {
        this.serverRoot = serverRoot.toAbsolutePath().normalize();
    }

    public Path getServerRoot() {
        return serverRoot;
    }

    /**
     * Sanitizes input strings against null bytes, drive letters, UNC paths, and encoded traversals.
     * Throws SecurityException if malicious encoding or traversal is detected.
     */
    public static String sanitizeInput(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // Layer 1.1: Reject raw null bytes
        if (input.indexOf('\0') != -1 || input.contains("%00")) {
            throw new SecurityException("Null byte injection detected");
        }

        // Layer 1.2: Detect URL-encoded traversal patterns (%2e, %2f, %252e, %5c)
        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.contains("%2e") || lower.contains("%252e") || lower.contains("%2f") || lower.contains("%5c")) {
            throw new SecurityException("URL-encoded path traversal detected");
        }

        // Decode hex percent without turning + into space
        String decoded = decodePercent(input);
        if (decoded.indexOf('\0') != -1 || decoded.contains("%00")) {
            throw new SecurityException("Null byte injection detected in decoded path");
        }
        if (decoded.contains("%")) {
            String doubleDecoded = decodePercent(decoded);
            if (doubleDecoded.indexOf('\0') != -1 || doubleDecoded.contains("%00")) {
                throw new SecurityException("Null byte injection detected in double-decoded path");
            }
        }

        // Layer 1.3: Reject Windows UNC paths
        if (input.startsWith("\\\\") || input.startsWith("//") || decoded.startsWith("\\\\") || decoded.startsWith("//")) {
            throw new SecurityException("UNC network paths forbidden");
        }

        // Layer 1.4: Normalize slashes while keeping literal filename intact
        String clean = input.replace('\\', '/');

        // Layer 1.5: Reject Windows drive letters (e.g. C:, D:\, C:/)
        if (clean.matches("^[a-zA-Z]:.*") || clean.contains(":/")) {
            throw new SecurityException("Windows drive letters forbidden");
        }

        return clean;
    }

    private static String decodePercent(String input) {
        if (input == null || input.isEmpty() || input.indexOf('%') == -1) {
            return input;
        }
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '%' && i + 2 < input.length()) {
                int d1 = Character.digit(input.charAt(i + 1), 16);
                int d2 = Character.digit(input.charAt(i + 2), 16);
                if (d1 != -1 && d2 != -1) {
                    baos.write((d1 << 4) + d2);
                    i += 2;
                    continue;
                }
            }
            byte[] charBytes = Character.toString(c).getBytes(StandardCharsets.UTF_8);
            baos.write(charBytes, 0, charBytes.length);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Resolves a virtual path given the current directory and an input path.
     * Clamps at "/" for navigation commands (CWD, CDUP).
     * Rejects traversal outside root with SecurityException for file operations (RETR, STOR, DELE, etc.).
     */
    public String resolveVirtual(String currentVirtualDir, String inputPath, boolean isNavigateCommand) {
        String clean = sanitizeInput(inputPath);
        if (clean.isEmpty()) {
            return currentVirtualDir == null || currentVirtualDir.isEmpty() ? "/" : currentVirtualDir;
        }

        List<String> segments = new ArrayList<>();
        if (!clean.startsWith("/")) {
            // Relative path: start with segments of current virtual directory
            if (currentVirtualDir != null && !currentVirtualDir.isEmpty()) {
                for (String part : currentVirtualDir.split("/")) {
                    if (!part.isEmpty()) {
                        segments.add(part);
                    }
                }
            }
        }

        for (String part : clean.split("/")) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!segments.isEmpty()) {
                    segments.remove(segments.size() - 1);
                } else {
                    if (!isNavigateCommand) {
                        throw new SecurityException("Path traversal outside virtual root");
                    }
                    // For navigate commands (CWD), clamp at root /
                }
            } else {
                segments.add(part);
            }
        }

        if (segments.isEmpty()) {
            return "/";
        }
        return "/" + String.join("/", segments);
    }

    /**
     * Maps a virtual path to the physical file system and asserts real boundary confinement.
     */
    public Path resolvePhysical(String virtualPath) {
        if (virtualPath == null || virtualPath.isEmpty() || "/".equals(virtualPath)) {
            return server.SafePaths.resolveSafe(serverRoot, "");
        }
        String rel = virtualPath.startsWith("/") ? virtualPath.substring(1) : virtualPath;
        return server.SafePaths.resolveSafe(serverRoot, rel);
    }

    /**
     * Formats a file or directory entry in standard UNIX ls -l format with Locale.US English month names.
     * Format: <perms> <links> <owner> <group> <size> <date> <name>\r\n
     */
    public static String formatUnixListing(Path physicalFile, String filename) {
        boolean isDir = Files.isDirectory(physicalFile);
        String perms = isDir ? "drwxr-xr-x" : "-rw-r--r--";
        long size = 0L;
        if (!isDir) {
            try {
                size = Files.size(physicalFile);
            } catch (IOException ignored) {}
        }

        Instant fileTime;
        try {
            fileTime = Files.getLastModifiedTime(physicalFile).toInstant();
        } catch (Exception e) {
            fileTime = Instant.now();
        }

        Instant now = Instant.now();
        long days = ChronoUnit.DAYS.between(fileTime, now);
        String dateStr;
        if (days >= 0 && days < 180) {
            dateStr = DateTimeFormatter.ofPattern("MMM dd HH:mm", Locale.US)
                    .withZone(ZoneId.systemDefault())
                    .format(fileTime);
        } else {
            dateStr = DateTimeFormatter.ofPattern("MMM dd  yyyy", Locale.US)
                    .withZone(ZoneId.systemDefault())
                    .format(fileTime);
        }

        return String.format(Locale.US, "%s   1 ftp      ftp     %10d %s %s\r\n",
                perms, size, dateStr, filename);
    }
}
