package server;

import com.sun.net.httpserver.HttpExchange;

/**
 * Applies strict isolation headers to untrusted raw upstream content (e.g. from Fetch and IIS proxy).
 * Enforces CSP sandbox without allow-scripts or allow-same-origin, disabling script execution
 * and blocking access to same-origin APIs (such as /api/files).
 */
public final class UpstreamContentPolicy {

    public static final String CSP_HEADER_VALUE = "sandbox; form-action 'none'; frame-ancestors 'none'";

    private UpstreamContentPolicy() {}

    public static void apply(HttpExchange ex) {
        if (ex == null) return;
        ex.getResponseHeaders().set("Content-Security-Policy", CSP_HEADER_VALUE);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    }
}
