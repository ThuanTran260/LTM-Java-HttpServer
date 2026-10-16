package server;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Shared configuration for local file store between Web (HTTP) and FTP.
 * Contains no secrets or passwords in fields or toString.
 */
public record FileStoreConfig(Path root, long maxFileBytes, boolean allowWebGuest) {

    public static final String DEFAULT_ROOT = "ftp_workspace/server_root";
    public static final long DEFAULT_MAX_FILE_BYTES = 67108864L; // 64 MiB

    public FileStoreConfig {
        if (root == null) {
            throw new IllegalArgumentException("Root path cannot be null");
        }
        root = root.toAbsolutePath().normalize();
        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException("maxFileBytes must be positive: " + maxFileBytes);
        }
    }

    /**
     * Reads configuration from environment variables:
     * - FTP_ROOT: root path (default: ftp_workspace/server_root)
     * - FTP_MAX_FILE_BYTES: maximum file size per upload (default: 64 MiB)
     * - WEB_FILES_ALLOW_GUEST: allow read-only guest access on Web (default: false)
     */
    public static FileStoreConfig fromEnvironment() {
        String rootEnv = System.getenv("FTP_ROOT");
        Path rootPath = (rootEnv != null && !rootEnv.trim().isEmpty())
                ? Paths.get(rootEnv.trim())
                : Paths.get(DEFAULT_ROOT);

        String maxBytesEnv = System.getenv("FTP_MAX_FILE_BYTES");
        long maxBytes = DEFAULT_MAX_FILE_BYTES;
        if (maxBytesEnv != null && !maxBytesEnv.trim().isEmpty()) {
            try {
                maxBytes = Long.parseLong(maxBytesEnv.trim());
                if (maxBytes <= 0) {
                    throw new IllegalArgumentException("FTP_MAX_FILE_BYTES must be positive: " + maxBytesEnv);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid FTP_MAX_FILE_BYTES number: " + maxBytesEnv, e);
            }
        }

        String guestEnv = System.getenv("WEB_FILES_ALLOW_GUEST");
        boolean allowGuest = "true".equalsIgnoreCase(guestEnv != null ? guestEnv.trim() : null);

        return new FileStoreConfig(rootPath, maxBytes, allowGuest);
    }
}
