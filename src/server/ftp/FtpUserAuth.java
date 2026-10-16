package server.ftp;

import java.util.Locale;

/**
 * Handles user authentication and role-based authorization for Java FTP Server.
 */
public class FtpUserAuth {

    public enum Role {
        GUEST,
        USER,
        ADMIN
    }

    private static String getAdminPassword() {
        String env = System.getenv("FTP_ADMIN_PASSWORD");
        return (env != null && !env.isBlank()) ? env.trim() : "admin123";
    }

    public static boolean isDemoExplicitlyDisabled() {
        String env = System.getenv("FTP_ENABLE_DEMO_ACCOUNT");
        if (env != null && ("false".equalsIgnoreCase(env.trim()) || "0".equals(env.trim()))) {
            return true;
        }
        String prop = System.getProperty("ftp.enable.demo");
        return prop != null && ("false".equalsIgnoreCase(prop.trim()) || "0".equals(prop.trim()));
    }

    public static boolean isDemoAccountEnabled() {
        if (isDemoExplicitlyDisabled()) {
            return false;
        }
        String env = System.getenv("FTP_ENABLE_DEMO_ACCOUNT");
        if (env != null && ("true".equalsIgnoreCase(env.trim()) || "1".equals(env.trim()))) {
            return true;
        }
        return Boolean.getBoolean("ftp.enable.demo");
    }

    public static String getStudentPassword() {
        if (isDemoExplicitlyDisabled()) {
            return null;
        }
        String env = System.getenv("FTP_STUDENT_PASSWORD");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        if (isDemoAccountEnabled()) {
            return "123456";
        }
        return null;
    }

    /**
     * Authenticates a user with the provided credentials.
     * - Anonymous access accepts any password (GUEST role: read-only).
     * - Student access requires explicit demo configuration (FTP_ENABLE_DEMO_ACCOUNT=true
     *   or FTP_STUDENT_PASSWORD) and password "123456" by default (USER role: full permissions).
     *   If demo is not enabled, student login is rejected (530).
     * - Admin access requires password from FTP_ADMIN_PASSWORD or default "admin123".
     */
    public static boolean authenticate(String username, String password) {
        if (username == null) {
            return false;
        }
        String cleanUser = username.trim().toLowerCase(Locale.ROOT);
        if ("anonymous".equals(cleanUser)) {
            return true;
        }
        if ("student".equals(cleanUser)) {
            String studentPass = getStudentPassword();
            return studentPass != null && password != null && studentPass.equals(password.trim());
        }
        if ("admin".equals(cleanUser)) {
            return password != null && getAdminPassword().equals(password.trim());
        }
        return false;
    }

    /**
     * Resolves the role associated with the username.
     */
    public static Role getRole(String username) {
        if (username != null) {
            String u = username.trim().toLowerCase(Locale.ROOT);
            if ("admin".equals(u)) {
                return Role.ADMIN;
            }
            if ("student".equals(u) && getStudentPassword() != null) {
                return Role.USER;
            }
        }
        return Role.GUEST;
    }

    public static boolean canRead(Role role) {
        return role != null;
    }

    public static boolean canWrite(Role role) {
        return role == Role.ADMIN || role == Role.USER;
    }

    public static boolean canDelete(Role role) {
        return role == Role.ADMIN || role == Role.USER;
    }

    public static boolean canRename(Role role) {
        return role == Role.ADMIN || role == Role.USER;
    }
}
