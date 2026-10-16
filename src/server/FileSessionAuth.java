package server;

import com.sun.net.httpserver.HttpExchange;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import server.ftp.FtpUserAuth;

/**
 * Local-only session, authentication, and CSRF protection for Web File Explorer.
 */
public class FileSessionAuth implements AutoCloseable {

    public static final String COOKIE_NAME = "FileExplorerSession";
    public static final String EXPLORER_HEADER = "X-File-Explorer";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    public static final long IDLE_TIMEOUT_MILLIS = 30 * 60 * 1000L; // 30 minutes
    public static final long MAX_LIFETIME_MILLIS = 8 * 3600 * 1000L; // 8 hours
    public static final int MAX_SESSIONS = 32;

    public record SessionView(String user, FtpUserAuth.Role role, String csrfToken, String sessionToken) {
        @Override
        public String toString() {
            return "SessionView[user=" + user + ", role=" + role + ", csrfToken=***, sessionToken=***]";
        }
    }

    public static final class AuthException extends Exception {
        private final int statusCode;
        private final String code;

        public AuthException(int statusCode, String code, String message) {
            super(message);
            this.statusCode = statusCode;
            this.code = code;
        }

        public int statusCode() {
            return statusCode;
        }

        public String code() {
            return code;
        }
    }

    private static class SessionRecord {
        final String user;
        final FtpUserAuth.Role role;
        final String sessionToken;
        final String csrfToken;
        final long createdAt;
        volatile long lastAccess;

        SessionRecord(String user, FtpUserAuth.Role role, String sessionToken, String csrfToken, long now) {
            this.user = user;
            this.role = role;
            this.sessionToken = sessionToken;
            this.csrfToken = csrfToken;
            this.createdAt = now;
            this.lastAccess = now;
        }
    }

    private static class LoginThrottle {
        final List<Long> failures = new ArrayList<>();
    }

    private final String adminPassword;
    private final boolean allowGuest;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    private final ConcurrentHashMap<String, SessionRecord> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LoginThrottle> loginThrottles = new ConcurrentHashMap<>();

    public FileSessionAuth(String adminPassword, boolean allowGuest, Clock clock) {
        this.adminPassword = adminPassword != null ? adminPassword.trim() : null;
        this.allowGuest = allowGuest;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public static FileSessionAuth fromEnvironment(boolean allowGuest) {
        String adminPass = System.getenv("FTP_ADMIN_PASSWORD");
        return new FileSessionAuth(adminPass, allowGuest, Clock.systemUTC());
    }

    public Clock clock() {
        return clock;
    }

    /**
     * Verifies that the request comes from localhost loopback and matches Host/Origin constraints.
     */
    public void checkLocalRequest(HttpExchange ex) throws AuthException {
        if (ex == null) {
            throw new AuthException(403, "FORBIDDEN", "Missing exchange");
        }

        // 1. Strict loopback check on remote socket (never trust X-Forwarded-For)
        if (ex.getRemoteAddress() == null || ex.getRemoteAddress().getAddress() == null) {
            throw new AuthException(403, "FORBIDDEN", "Cannot determine remote address");
        }
        InetAddress remoteAddr = ex.getRemoteAddress().getAddress();
        if (!remoteAddr.isLoopbackAddress()) {
            throw new AuthException(403, "FORBIDDEN", "Access restricted to localhost loopback only");
        }

        // 2. Strict Host header check
        String host = ex.getRequestHeaders().getFirst("Host");
        if (host == null || host.isBlank()) {
            throw new AuthException(403, "FORBIDDEN", "Missing Host header");
        }
        String hostOnly = host.trim();
        int colonIdx = hostOnly.lastIndexOf(':');
        if (colonIdx > 0 && !hostOnly.endsWith("]")) {
            hostOnly = hostOnly.substring(0, colonIdx);
        }
        if (!hostOnly.equalsIgnoreCase("localhost") && !hostOnly.equals("127.0.0.1") && !hostOnly.equals("[::1]")) {
            throw new AuthException(403, "FORBIDDEN", "Invalid Host header: " + host);
        }

        // 3. Strict Origin header check if present
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.isBlank()) {
            String cleanOrigin = origin.trim();
            if ("null".equalsIgnoreCase(cleanOrigin)) {
                throw new AuthException(403, "FORBIDDEN", "Null Origin is forbidden");
            }
            try {
                URI originUri = URI.create(cleanOrigin);
                String scheme = originUri.getScheme();
                if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                    throw new AuthException(403, "FORBIDDEN", "Forbidden Origin scheme: " + scheme);
                }
                String originHost = originUri.getHost();
                if (originHost == null || (!originHost.equalsIgnoreCase("localhost")
                        && !originHost.equals("127.0.0.1") && !originHost.equals("[::1]"))) {
                    throw new AuthException(403, "FORBIDDEN", "Forbidden Origin host: " + originHost);
                }
            } catch (Exception e) {
                throw new AuthException(403, "FORBIDDEN", "Malformed Origin header");
            }
        }
    }

    /**
     * Authenticates username and password, returning a new SessionView.
     */
    public SessionView login(String username, String password) throws AuthException {
        long now = clock.millis();

        // Check if admin password is configured
        if (adminPassword == null || adminPassword.isEmpty()) {
            throw new AuthException(503, "SETUP_REQUIRED", "Web admin password is not configured; check FTP_ADMIN_PASSWORD");
        }

        // Rate limiting check per client
        String throttleKey = "loopback";
        LoginThrottle throttle = loginThrottles.computeIfAbsent(throttleKey, k -> new LoginThrottle());
        synchronized (throttle) {
            throttle.failures.removeIf(timestamp -> now - timestamp > 60000L);
            if (throttle.failures.size() >= 5) {
                throw new AuthException(429, "TOO_MANY_REQUESTS", "Too many failed login attempts; retry in 60 seconds");
            }
        }

        String u = username != null ? username.trim() : "";
        String p = password != null ? password : "";

        FtpUserAuth.Role authenticatedRole = null;
        if ("admin".equalsIgnoreCase(u) && adminPassword.equals(p)) {
            authenticatedRole = FtpUserAuth.Role.ADMIN;
        } else if (allowGuest && "guest".equalsIgnoreCase(u)) {
            authenticatedRole = FtpUserAuth.Role.GUEST;
        }

        if (authenticatedRole == null) {
            synchronized (throttle) {
                throttle.failures.add(now);
            }
            throw new AuthException(401, "UNAUTHORIZED", "Invalid username or password");
        }

        // Generate tokens
        byte[] sessionBytes = new byte[32];
        byte[] csrfBytes = new byte[32];
        random.nextBytes(sessionBytes);
        random.nextBytes(csrfBytes);

        String sessionToken = Base64.getUrlEncoder().withoutPadding().encodeToString(sessionBytes);
        String csrfToken = Base64.getUrlEncoder().withoutPadding().encodeToString(csrfBytes);

        // Enforce max sessions bound
        evictExpiredSessions(now);
        if (sessions.size() >= MAX_SESSIONS) {
            evictOldestSession();
        }

        SessionRecord record = new SessionRecord(u, authenticatedRole, sessionToken, csrfToken, now);
        sessions.put(sessionToken, record);

        return new SessionView(record.user, record.role, record.csrfToken, record.sessionToken);
    }

    /**
     * Validates session from cookie and checks CSRF if write is required.
     */
    public SessionView requireSession(HttpExchange ex, boolean write) throws AuthException {
        checkLocalRequest(ex);

        if (write) {
            String explorerHeader = ex.getRequestHeaders().getFirst(EXPLORER_HEADER);
            if (!"1".equals(explorerHeader)) {
                throw new AuthException(403, "FORBIDDEN", "Missing or invalid " + EXPLORER_HEADER + " header");
            }
        }

        String sessionToken = extractSessionCookie(ex);
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new AuthException(401, "UNAUTHORIZED", "Authentication required");
        }

        SessionRecord record = sessions.get(sessionToken);
        if (record == null) {
            throw new AuthException(401, "UNAUTHORIZED", "Invalid session");
        }

        long now = clock.millis();
        if (now - record.createdAt > MAX_LIFETIME_MILLIS || now - record.lastAccess > IDLE_TIMEOUT_MILLIS) {
            sessions.remove(sessionToken);
            throw new AuthException(401, "UNAUTHORIZED", "Session expired");
        }

        record.lastAccess = now;

        if (write) {
            if (record.role == FtpUserAuth.Role.GUEST) {
                throw new AuthException(403, "FORBIDDEN", "Guest account is read-only");
            }

            String csrfHeader = ex.getRequestHeaders().getFirst(CSRF_HEADER);
            if (csrfHeader == null || !record.csrfToken.equals(csrfHeader.trim())) {
                throw new AuthException(403, "FORBIDDEN", "Invalid or missing CSRF token");
            }
        }

        return new SessionView(record.user, record.role, record.csrfToken, record.sessionToken);
    }

    public void logout(HttpExchange ex) {
        String sessionToken = extractSessionCookie(ex);
        if (sessionToken != null) {
            sessions.remove(sessionToken);
        }
    }

    public static String createCookieHeader(String sessionToken) {
        return COOKIE_NAME + "=" + sessionToken + "; HttpOnly; SameSite=Strict; Path=/api/files";
    }

    public static String createDeleteCookieHeader() {
        return COOKIE_NAME + "=; HttpOnly; SameSite=Strict; Path=/api/files; Max-Age=0";
    }

    private String extractSessionCookie(HttpExchange ex) {
        List<String> cookieHeaders = ex.getRequestHeaders().get("Cookie");
        if (cookieHeaders == null) return null;
        for (String h : cookieHeaders) {
            for (String cookie : h.split(";")) {
                String[] parts = cookie.split("=", 2);
                if (parts.length == 2 && COOKIE_NAME.equalsIgnoreCase(parts[0].trim())) {
                    return parts[1].trim();
                }
            }
        }
        return null;
    }

    private void evictExpiredSessions(long now) {
        sessions.entrySet().removeIf(entry -> {
            SessionRecord rec = entry.getValue();
            return (now - rec.createdAt > MAX_LIFETIME_MILLIS) || (now - rec.lastAccess > IDLE_TIMEOUT_MILLIS);
        });
    }

    private void evictOldestSession() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        for (Map.Entry<String, SessionRecord> e : sessions.entrySet()) {
            if (e.getValue().createdAt < oldestTime) {
                oldestTime = e.getValue().createdAt;
                oldestKey = e.getKey();
            }
        }
        if (oldestKey != null) {
            sessions.remove(oldestKey);
        }
    }

    @Override
    public void close() {
        sessions.clear();
        loginThrottles.clear();
    }
}
