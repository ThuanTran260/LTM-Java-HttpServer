package client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a remote FTP directory entry.
 * Supports dual parsing:
 * 1. UNIX "ls -l" style format (Simple Java FTP Server, FileZilla, Linux FTP daemons).
 * 2. DOS style format (Microsoft Windows IIS FTP Server).
 */
public class FtpFile {

    private static final Pattern DOS_PATTERN = Pattern.compile(
        "^(\\d{2}-\\d{2}-\\d{2,4}\\s+\\d{1,2}:\\d{2}\\s*(?:AM|PM|am|pm)?)\\s+(<DIR>|\\d+)\\s+(.+)$"
    );

    private static final Pattern UNIX_PATTERN = Pattern.compile(
        "^([\\-dlcbsp])([rwx\\-tTsS]{9})\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)\\s+([A-Za-z]{3}\\s+\\d{1,2}\\s+(?:\\d{4}|[\\d:]{4,5}))\\s+(.+)$"
    );

    // Secondary fallback UNIX pattern (flexible owner/group)
    private static final Pattern UNIX_FLEX_PATTERN = Pattern.compile(
        "^([\\-dlcbsp])([rwx\\-tTsS]{9})\\s+.*?(\\d+)\\s+([A-Za-z]{3}\\s+\\d{1,2}\\s+(?:\\d{4}|[\\d:]{4,5}))\\s+(.+)$"
    );

    private final String name;
    private final long size;
    private final boolean isDirectory;
    private final String lastModified;
    private final String rawListing;

    public FtpFile(String name, long size, boolean isDirectory, String lastModified, String rawListing) {
        this.name = name != null ? name : "";
        this.size = size;
        this.isDirectory = isDirectory;
        this.lastModified = lastModified != null ? lastModified : "";
        this.rawListing = rawListing != null ? rawListing : "";
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public String getLastModified() {
        return lastModified;
    }

    public String getRawListing() {
        return rawListing;
    }

    /**
     * Parses a single line of directory listing from either UNIX or DOS FTP servers.
     *
     * @param line raw line from FTP LIST command
     * @return parsed FtpFile instance, or null if line is empty/header
     */
    public static FtpFile parse(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("total ") || trimmed.startsWith("Directory of ")) {
            return null; // Ignore summary headers
        }

        // 1. Try DOS format first (e.g. Windows IIS FTP)
        Matcher dosMatcher = DOS_PATTERN.matcher(trimmed);
        if (dosMatcher.matches()) {
            String timestamp = dosMatcher.group(1).trim();
            String dirOrSize = dosMatcher.group(2).trim();
            String name = dosMatcher.group(3).trim();
            boolean isDir = "<DIR>".equalsIgnoreCase(dirOrSize);
            long size = 0L;
            if (!isDir) {
                try {
                    size = Long.parseLong(dirOrSize);
                } catch (NumberFormatException ignored) {}
            }
            return new FtpFile(name, size, isDir, timestamp, line);
        }

        // 2. Try Standard UNIX ls -l format (Java FTP Server / FileZilla)
        Matcher unixMatcher = UNIX_PATTERN.matcher(trimmed);
        if (unixMatcher.matches()) {
            boolean isDir = "d".equalsIgnoreCase(unixMatcher.group(1));
            long size = 0L;
            try {
                size = Long.parseLong(unixMatcher.group(3).trim());
            } catch (NumberFormatException ignored) {}
            String timestamp = unixMatcher.group(4).trim();
            String rawName = unixMatcher.group(5).trim();
            String name = cleanUnixName(rawName);
            return new FtpFile(name, size, isDir, timestamp, line);
        }

        // 3. Try Flexible UNIX pattern
        Matcher flexMatcher = UNIX_FLEX_PATTERN.matcher(trimmed);
        if (flexMatcher.matches()) {
            boolean isDir = "d".equalsIgnoreCase(flexMatcher.group(1));
            long size = 0L;
            try {
                size = Long.parseLong(flexMatcher.group(3).trim());
            } catch (NumberFormatException ignored) {}
            String timestamp = flexMatcher.group(4).trim();
            String name = cleanUnixName(flexMatcher.group(5).trim());
            return new FtpFile(name, size, isDir, timestamp, line);
        }

        // 4. Token-based fallback if format is non-standard
        if (trimmed.startsWith("d") || trimmed.startsWith("-")) {
            boolean isDir = trimmed.startsWith("d");
            String[] tokens = trimmed.split("\\s+");
            if (tokens.length >= 9) {
                long size = 0L;
                try {
                    size = Long.parseLong(tokens[4]);
                } catch (Exception ignored) {}
                String date = tokens[5] + " " + tokens[6] + " " + tokens[7];
                // Join tokens 8..N for name with spaces
                StringBuilder nameBuilder = new StringBuilder(tokens[8]);
                for (int i = 9; i < tokens.length; i++) {
                    nameBuilder.append(" ").append(tokens[i]);
                }
                return new FtpFile(cleanUnixName(nameBuilder.toString()), size, isDir, date, line);
            }
        }

        // Minimal fallback
        return new FtpFile(trimmed, 0L, false, "", line);
    }

    private static String cleanUnixName(String rawName) {
        if (rawName == null) return "";
        // If symlink: "name -> target", extract the link name
        int arrow = rawName.indexOf(" -> ");
        if (arrow != -1) {
            return rawName.substring(0, arrow).trim();
        }
        return rawName.trim();
    }

    /**
     * Parses a multi-line directory listing response into a list of FtpFile objects.
     */
    public static List<FtpFile> parseList(String listing) {
        List<FtpFile> result = new ArrayList<>();
        if (listing == null || listing.isBlank()) {
            return result;
        }
        String[] lines = listing.split("\\r?\\n");
        for (String line : lines) {
            FtpFile file = parse(line);
            if (file != null && !file.getName().isEmpty() && !file.getName().equals(".") && !file.getName().equals("..")) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * Returns a human-readable file size string (e.g. "1.5 MB", "1024 B", "<DIR>").
     */
    public String getFormattedSize() {
        if (isDirectory) {
            return "<DIR>";
        }
        if (size < 1024) {
            return size + " B";
        }
        int exp = (int) (Math.log(size) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format(java.util.Locale.ROOT, "%.1f %cB", size / Math.pow(1024, exp), unit);
    }

    public String getType() {
        if (isDirectory) {
            return "Folder";
        }
        int dot = name.lastIndexOf('.');
        if (dot != -1 && dot < name.length() - 1) {
            return name.substring(dot + 1).toUpperCase(java.util.Locale.ROOT) + " File";
        }
        return "File";
    }

    @Override
    public String toString() {
        return (isDirectory ? "[DIR] " : "      ") + String.format("%-25s %10s %s", name, getFormattedSize(), lastModified);
    }
}
