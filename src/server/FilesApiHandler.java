package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP REST API Handler for local Web File Explorer.
 * Context: /api/files
 */
public class FilesApiHandler implements HttpHandler {

    private final SharedFileStore store;
    private final FileSessionAuth auth;
    private final Semaphore uploadSemaphore = new Semaphore(2);

    private static final ScheduledThreadPoolExecutor SCHEDULER;
    static {
        SCHEDULER = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "files-api-upload-timeout");
            t.setDaemon(true);
            return t;
        });
        SCHEDULER.setRemoveOnCancelPolicy(true);
    }

    public FilesApiHandler(SharedFileStore store, FileSessionAuth auth) {
        if (auth == null) throw new IllegalArgumentException("FileSessionAuth cannot be null");
        this.store = store;
        this.auth = auth;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);

        // Security headers for all API responses
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

        try {
            // Strict exact route dispatching (reject /api/filesXYZ with 404 JSON)
            if (path.equals("/api/files") || path.equals("/api/files/")) {
                if (method.equals("GET")) {
                    handleList(ex);
                } else if (method.equals("DELETE")) {
                    handleDelete(ex);
                } else {
                    sendMethodNotAllowed(ex, "GET, DELETE");
                }
            } else if (path.equals("/api/files/login")) {
                if (method.equals("POST")) {
                    handleLogin(ex);
                } else {
                    sendMethodNotAllowed(ex, "POST");
                }
            } else if (path.equals("/api/files/session")) {
                if (method.equals("GET")) {
                    handleSession(ex);
                } else {
                    sendMethodNotAllowed(ex, "GET");
                }
            } else if (path.equals("/api/files/logout")) {
                if (method.equals("POST")) {
                    handleLogout(ex);
                } else {
                    sendMethodNotAllowed(ex, "POST");
                }
            } else if (path.equals("/api/files/download")) {
                if (method.equals("GET")) {
                    handleDownload(ex);
                } else {
                    sendMethodNotAllowed(ex, "GET");
                }
            } else if (path.equals("/api/files/content")) {
                if (method.equals("PUT")) {
                    handleUpload(ex);
                } else {
                    sendMethodNotAllowed(ex, "PUT");
                }
            } else if (path.equals("/api/files/mkdir")) {
                if (method.equals("POST")) {
                    handleMkdir(ex);
                } else {
                    sendMethodNotAllowed(ex, "POST");
                }
            } else if (path.equals("/api/files/rename")) {
                if (method.equals("POST")) {
                    handleRename(ex);
                } else {
                    sendMethodNotAllowed(ex, "POST");
                }
            } else {
                sendNotFound(ex);
            }
        } catch (FileSessionAuth.AuthException ae) {
            sendJson(ex, ae.statusCode(), ae.code(), ae.getMessage());
        } catch (SharedFileStore.StoreException se) {
            int status = mapStoreStatusCode(se.code());
            sendJson(ex, status, se.code(), se.getMessage());
        } catch (SecurityException se) {
            sendJson(ex, 403, "FORBIDDEN", se.getMessage());
        } catch (IllegalArgumentException iae) {
            sendJson(ex, 400, "BAD_REQUEST", iae.getMessage());
        } catch (Exception e) {
            sendJson(ex, 500, "INTERNAL_ERROR", "Unexpected server error: " + e.getMessage());
        }
    }

    // =========================================================================
    // ROUTE HANDLERS
    // =========================================================================

    private void handleLogin(HttpExchange ex) throws Exception {
        auth.checkLocalRequest(ex);

        // Login requires X-File-Explorer header
        String expHeader = ex.getRequestHeaders().getFirst(FileSessionAuth.EXPLORER_HEADER);
        if (!"1".equals(expHeader)) {
            sendJson(ex, 403, "FORBIDDEN", "Missing or invalid " + FileSessionAuth.EXPLORER_HEADER + " header");
            return;
        }

        String ct = ex.getRequestHeaders().getFirst("Content-Type");
        if (ct == null || !ct.toLowerCase(Locale.ROOT).contains("application/json")) {
            sendJson(ex, 415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json");
            return;
        }

        String body = readBoundedBody(ex, 16384);
        String username = extractJsonField(body, "username");
        String password = extractJsonField(body, "password");

        FileSessionAuth.SessionView view = auth.login(username, password);

        ex.getResponseHeaders().set("Set-Cookie", FileSessionAuth.createCookieHeader(view.sessionToken()));
        String json = "{\"user\":\"" + escapeJson(view.user()) + "\",\"role\":\"" + view.role()
                + "\",\"csrfToken\":\"" + escapeJson(view.csrfToken()) + "\"}";
        sendJsonRaw(ex, 200, json);
    }

    private void handleSession(HttpExchange ex) throws Exception {
        FileSessionAuth.SessionView view = auth.requireSession(ex, false);
        String json = "{\"user\":\"" + escapeJson(view.user()) + "\",\"role\":\"" + view.role()
                + "\",\"csrfToken\":\"" + escapeJson(view.csrfToken()) + "\"}";
        sendJsonRaw(ex, 200, json);
    }

    private void handleLogout(HttpExchange ex) throws Exception {
        auth.requireSession(ex, true);
        auth.logout(ex);
        ex.getResponseHeaders().set("Set-Cookie", FileSessionAuth.createDeleteCookieHeader());
        ex.sendResponseHeaders(204, -1);
        ex.close();
    }

    private void handleList(HttpExchange ex) throws Exception {
        auth.requireSession(ex, false);
        if (store == null) {
            sendJson(ex, 503, "SETUP_REQUIRED", "File storage is not initialized; check root path configuration");
            return;
        }

        Map<String, String> query = parseQueryParams(ex.getRequestURI().getRawQuery());
        String dirPath = query.getOrDefault("path", "/");
        int offset = 0;
        int limit = 200;

        if (query.containsKey("offset")) {
            try { offset = Math.max(0, Integer.parseInt(query.get("offset"))); } catch (NumberFormatException ignored) {}
        }
        if (query.containsKey("limit")) {
            try { limit = Math.max(1, Math.min(200, Integer.parseInt(query.get("limit")))); } catch (NumberFormatException ignored) {}
        }

        SharedFileStore.DirectoryPage page = store.list(dirPath, offset, limit);

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"path\":\"").append(escapeJson(page.path())).append("\",");
        sb.append("\"parent\":").append(page.parent() != null ? "\"" + escapeJson(page.parent()) + "\"" : "null").append(",");
        sb.append("\"offset\":").append(page.offset()).append(",");
        sb.append("\"limit\":").append(page.limit()).append(",");
        sb.append("\"total\":").append(page.total()).append(",");
        sb.append("\"entries\":[");
        for (int i = 0; i < page.entries().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(entryJson(page.entries().get(i)));
        }
        sb.append("]}");

        sendJsonRaw(ex, 200, sb.toString());
    }

    private void handleDownload(HttpExchange ex) throws Exception {
        auth.requireSession(ex, false);
        if (store == null) {
            sendJson(ex, 503, "SETUP_REQUIRED", "File storage is not initialized; check root path configuration");
            return;
        }

        Map<String, String> query = parseQueryParams(ex.getRequestURI().getRawQuery());
        String filePath = query.get("path");
        if (filePath == null || filePath.isBlank()) {
            sendJson(ex, 400, "BAD_REQUEST", "Missing 'path' query parameter");
            return;
        }

        try (SharedFileStore.Download dl = store.openDownload(filePath)) {
            String name = dl.name();
            String asciiFallback = name.replaceAll("[^a-zA-Z0-9._-]", "_");
            String utf8Encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");

            ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
            ex.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + utf8Encoded);

            long size = dl.size();
            ex.sendResponseHeaders(200, size);
            if (size > 0) {
                try (InputStream in = dl.stream(); OutputStream out = ex.getResponseBody()) {
                    in.transferTo(out);
                    out.flush();
                }
            } else {
                ex.close();
            }
        }
    }

    private void handleUpload(HttpExchange ex) throws Exception {
        auth.requireSession(ex, true);
        if (store == null) {
            sendJson(ex, 503, "SETUP_REQUIRED", "File storage is not initialized; check root path configuration");
            return;
        }

        Map<String, String> query = parseQueryParams(ex.getRequestURI().getRawQuery());
        String targetPath = query.get("path");
        if (targetPath == null || targetPath.isBlank()) {
            sendJson(ex, 400, "BAD_REQUEST", "Missing 'path' query parameter");
            return;
        }

        String sizeHeader = ex.getRequestHeaders().getFirst("X-File-Size");
        if (sizeHeader == null || sizeHeader.isBlank()) {
            sendJson(ex, 400, "BAD_REQUEST", "Missing mandatory X-File-Size header");
            return;
        }

        long expectedBytes;
        try {
            expectedBytes = Long.parseLong(sizeHeader.trim());
            if (expectedBytes < 0) {
                sendJson(ex, 400, "BAD_REQUEST", "X-File-Size must be non-negative");
                return;
            }
        } catch (NumberFormatException nfe) {
            sendJson(ex, 400, "BAD_REQUEST", "Invalid X-File-Size header value");
            return;
        }

        if (expectedBytes > store.maxFileBytes()) {
            sendJson(ex, 413, "TOO_LARGE", "File size exceeds limit of " + store.maxFileBytes() + " bytes");
            return;
        }

        // Limit to 2 concurrent uploads
        if (!uploadSemaphore.tryAcquire()) {
            sendJson(ex, 429, "BUSY", "Maximum concurrent uploads reached (2 uploads in progress)");
            return;
        }

        try {
            ScheduledFuture<?> timeoutTask = SCHEDULER.schedule(() -> {
                try { ex.close(); } catch (Exception ignored) {}
            }, 120, TimeUnit.SECONDS);

            try (InputStream in = new WatchdogInputStream(ex.getRequestBody(), SCHEDULER, ex, 30_000)) {
                SharedFileStore.Entry entry = store.upload(targetPath, in, expectedBytes, false);
                timeoutTask.cancel(true);
                sendJsonRaw(ex, 201, entryJson(entry));
            } finally {
                timeoutTask.cancel(true);
            }
        } finally {
            uploadSemaphore.release();
        }
    }

    private void handleMkdir(HttpExchange ex) throws Exception {
        auth.requireSession(ex, true);
        if (store == null) {
            sendJson(ex, 503, "SETUP_REQUIRED", "File storage is not initialized; check root path configuration");
            return;
        }

        String ct = ex.getRequestHeaders().getFirst("Content-Type");
        if (ct == null || !ct.toLowerCase(Locale.ROOT).contains("application/json")) {
            sendJson(ex, 415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json");
            return;
        }

        String body = readBoundedBody(ex, 16384);
        String dirPath = extractJsonField(body, "path");
        if (dirPath == null || dirPath.isBlank()) {
            sendJson(ex, 400, "BAD_REQUEST", "Missing 'path' field in JSON request body");
            return;
        }

        SharedFileStore.Entry entry = store.mkdir(dirPath);
        sendJsonRaw(ex, 201, entryJson(entry));
    }

    private void handleRename(HttpExchange ex) throws Exception {
        auth.requireSession(ex, true);
        if (store == null) {
            sendJson(ex, 503, "SETUP_REQUIRED", "File storage is not initialized; check root path configuration");
            return;
        }

        String ct = ex.getRequestHeaders().getFirst("Content-Type");
        if (ct == null || !ct.toLowerCase(Locale.ROOT).contains("application/json")) {
            sendJson(ex, 415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json");
            return;
        }

        String body = readBoundedBody(ex, 16384);
        String from = extractJsonField(body, "from");
        String to = extractJsonField(body, "to");
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            sendJson(ex, 400, "BAD_REQUEST", "Missing 'from' or 'to' fields in JSON request body");
            return;
        }

        SharedFileStore.Entry entry = store.rename(from, to);
        sendJsonRaw(ex, 200, entryJson(entry));
    }

    private void handleDelete(HttpExchange ex) throws Exception {
        auth.requireSession(ex, true);
        if (store == null) {
            sendJson(ex, 503, "SETUP_REQUIRED", "File storage is not initialized; check root path configuration");
            return;
        }

        Map<String, String> query = parseQueryParams(ex.getRequestURI().getRawQuery());
        String targetPath = query.get("path");
        if (targetPath == null || targetPath.isBlank()) {
            sendJson(ex, 400, "BAD_REQUEST", "Missing 'path' query parameter");
            return;
        }

        store.delete(targetPath);
        ex.sendResponseHeaders(204, -1);
        ex.close();
    }

    // =========================================================================
    // UTILITY HELPERS
    // =========================================================================

    private void sendJson(HttpExchange ex, int statusCode, String code, String message) throws IOException {
        String json = "{\"code\":\"" + escapeJson(code) + "\",\"message\":\"" + escapeJson(message) + "\"}";
        sendJsonRaw(ex, statusCode, json);
    }

    private void sendJsonRaw(HttpExchange ex, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendMethodNotAllowed(HttpExchange ex, String allowHeader) throws IOException {
        ex.getResponseHeaders().set("Allow", allowHeader);
        sendJson(ex, 405, "METHOD_NOT_ALLOWED", "Method Not Allowed");
    }

    private void sendNotFound(HttpExchange ex) throws IOException {
        sendJson(ex, 404, "NOT_FOUND", "Route not found");
    }

    private int mapStoreStatusCode(String code) {
        return switch (code) {
            case "NOT_FOUND" -> 404;
            case "FORBIDDEN" -> 403;
            case "CONFLICT", "NOT_EMPTY", "BUSY" -> 409;
            case "TOO_LARGE" -> 413;
            case "TOO_MANY_ENTRIES" -> 422;
            case "ATOMIC_UNSUPPORTED", "SETUP_REQUIRED" -> 503;
            case "NO_SPACE" -> 507;
            default -> 500;
        };
    }

    private static String readBoundedBody(HttpExchange ex, int maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        InputStream in = ex.getRequestBody();
        int r;
        while ((r = in.read(buf)) != -1) {
            baos.write(buf, 0, r);
            if (baos.size() > maxBytes) {
                throw new IllegalArgumentException("Request body exceeds maximum allowed size of " + maxBytes + " bytes");
            }
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) return map;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String k = decodePercent(pair.substring(0, eq));
                String v = decodePercent(pair.substring(eq + 1));
                map.put(k, v);
            } else if (eq == -1 && !pair.isBlank()) {
                String k = decodePercent(pair);
                map.put(k, "");
            }
        }
        return map;
    }

    private static String decodePercent(String input) {
        if (input == null || input.isEmpty()) return input;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
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

    private static String extractJsonField(String json, String field) {
        if (json == null) return null;
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"");
        Matcher m = pattern.matcher(json);
        if (m.find()) {
            return unescapeJson(m.group(1));
        }
        return null;
    }

    private static String unescapeJson(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"' -> { sb.append('"'); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case '/' -> { sb.append('/'); i++; }
                    case 'b' -> { sb.append('\b'); i++; }
                    case 'f' -> { sb.append('\f'); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    case 'u' -> {
                        if (i + 5 < s.length()) {
                            try {
                                int hex = Integer.parseInt(s.substring(i + 2, i + 6), 16);
                                sb.append((char) hex);
                                i += 5;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                    }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String entryJson(SharedFileStore.Entry e) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"name\":\"").append(escapeJson(e.name())).append("\",");
        sb.append("\"path\":\"").append(escapeJson(e.path())).append("\",");
        sb.append("\"directory\":").append(e.directory()).append(",");
        sb.append("\"size\":").append(e.size() != null ? e.size() : "null").append(",");
        sb.append("\"modified\":").append(e.modified() != null ? "\"" + e.modified().toString() + "\"" : "null").append(",");
        sb.append("\"blocked\":").append(e.blocked());
        sb.append("}");
        return sb.toString();
    }

    private static class WatchdogInputStream extends FilterInputStream {
        private final ScheduledThreadPoolExecutor scheduler;
        private final HttpExchange ex;
        private final long timeoutMs;
        private volatile ScheduledFuture<?> task;

        WatchdogInputStream(InputStream in, ScheduledThreadPoolExecutor scheduler, HttpExchange ex, long timeoutMs) {
            super(in);
            this.scheduler = scheduler;
            this.ex = ex;
            this.timeoutMs = timeoutMs;
            resetTimer();
        }

        private synchronized void resetTimer() {
            ScheduledFuture<?> old = task;
            if (old != null) old.cancel(true);
            task = scheduler.schedule(() -> {
                try { ex.close(); } catch (Exception ignored) {}
            }, timeoutMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public int read() throws IOException {
            resetTimer();
            int b = super.read();
            resetTimer();
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            resetTimer();
            int r = super.read(b, off, len);
            resetTimer();
            return r;
        }

        @Override
        public void close() throws IOException {
            synchronized (this) {
                ScheduledFuture<?> old = task;
                if (old != null) old.cancel(true);
            }
            super.close();
        }
    }
}
