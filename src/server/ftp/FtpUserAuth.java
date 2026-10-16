package server.ftp;

import java.util.Locale;

/**
 * Handles user authentication and role-based authorization for Java FTP Server.
 */
public class FtpUserAuth {

    public enum Role {
        GUEST,
        ADMIN
    }

    /**
     * Authenticates a user with the provided credentials.
     * Anonymous access accepts any password.
     * Admin access requires password "admin123".
     */
    public static boolean authenticate(String username, String password) {
        if (username == null) {
            return false;
        }
        String cleanUser = username.trim().toLowerCase(Locale.ROOT);
        if ("anonymous".equals(cleanUser)) {
            return true;
        }
        if ("admin".equals(cleanUser)) {
            return password != null && "admin123".equals(password.trim());
        }
        return false;
    }

    /**
     * Resolves the role associated with the username.
     */
    public static Role getRole(String username) {
        if (username != null && "admin".equalsIgnoreCase(username.trim())) {
            return Role.ADMIN;
        }
        return Role.GUEST;
    }

    public static boolean canRead(Role role) {
        return role != null;
    }

    public static boolean canWrite(Role role) {
        return role == Role.ADMIN;
    }

    public static boolean canDelete(Role role) {
        return role == Role.ADMIN;
    }
}
