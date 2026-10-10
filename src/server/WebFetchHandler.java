package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Web Fetcher / Proxy Handler:
 * - GET /fetch?url=<target_url> & GET /api/fetch?url=...
 * - Chi chap nhan scheme http:// va https:// (tu choi file://, ftp://, javascript:, ...) -> 400 Bad Request
 * - Cho phep fetch ca domain internet va localhost (IIS :80/:8081)
 * - Mac dinh tra ve Raw content (HTML/text/anh) voi Content-Type goc
 * - Tu dong chen the <base href="TARGET_URL"> vao <head> neu noi dung la HTML (text/html)
 * - format=json tra ve JSON metadata: targetUrl, status, contentType, contentLength, body
 */
public class WebFetchHandler implements HttpHandler {

    private static final HttpClient CLIENT = createHttpClient();
    private static final Pattern HEAD_PATTERN = Pattern.compile("(?i)<head(\\s[^>]*)?>");
    private static final Pattern HTML_PATTERN = Pattern.compile("(?i)<html(\\s[^>]*)?>");
    private static final Pattern DOCTYPE_PATTERN = Pattern.compile("(?i)<!doctype\\s+html[^>]*>");

    private static HttpClient createHttpClient() {
        try {
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(null, new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[0];
                    }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            }, new java.security.SecureRandom());

            return HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        } catch (Exception e) {
            return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        }
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method)) {
            ex.getResponseHeaders().set("Allow", "GET");
            RouterHandler.send(ex, 405, "text/plain; charset=utf-8", "405 Method Not Allowed");
            return;
        }

        String query = ex.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            RouterHandler.send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request: missing url parameter");
            return;
        }

        String targetUrl = extractUrlParam(query);
        if (targetUrl == null || targetUrl.isBlank()) {
            RouterHandler.send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request: missing url parameter");
            return;
        }

        // Kiem tra hop le cua URL & Scheme (Q3)
        URI uri;
        try {
            uri = URI.create(targetUrl.trim());
        } catch (Exception e) {
            RouterHandler.send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request: invalid URL syntax");
            return;
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            RouterHandler.send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request: missing scheme (must be http:// or https://)");
            return;
        }

        String lowerScheme = scheme.toLowerCase(Locale.ROOT);
        if (!lowerScheme.equals("http") && !lowerScheme.equals("https")) {
            RouterHandler.send(ex, 400, "text/plain; charset=utf-8",
                "400 Bad Request: unsupported scheme '" + scheme + "'. Only http and https are allowed.");
            return;
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            RouterHandler.send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request: missing host in URL");
            return;
        }

        String format = RouterHandler.queryParam(query, "format");
        boolean isJson = "json".equalsIgnoreCase(format);

        fetchAndRespond(ex, uri, targetUrl, isJson);
    }

    private void fetchAndRespond(HttpExchange ex, URI uri, String targetUrl, boolean isJson) throws IOException {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Java-HttpServer-WebFetcher/1.0")
                .GET()
                .build();
        } catch (Exception e) {
            RouterHandler.send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request: " + e.getMessage());
            return;
        }

        HttpResponse<byte[]> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            String msg = "502 Bad Gateway: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            if (isJson) {
                byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
                String json = buildJsonMetadata(targetUrl, 502, "text/plain; charset=utf-8", msgBytes.length, msg);
                RouterHandler.send(ex, 502, "application/json; charset=utf-8", json);
            } else {
                RouterHandler.send(ex, 502, "text/plain; charset=utf-8", msg);
            }
            return;
        }

        int statusCode = response.statusCode();
        String rawCt = response.headers().firstValue("Content-Type").orElse("text/html; charset=utf-8");
        byte[] bodyBytes = response.body() != null ? response.body() : new byte[0];

        // Giai nen GZIP neu upstream tra ve gzip
        String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("");
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            try (ByteArrayInputStream bis = new ByteArrayInputStream(bodyBytes);
                 GZIPInputStream gis = new GZIPInputStream(bis)) {
                bodyBytes = gis.readAllBytes();
            } catch (Exception ignored) {}
        }

        boolean isHtml = rawCt.toLowerCase(Locale.ROOT).contains("text/html");
        byte[] finalBytes = bodyBytes;
        String bodyString = null;

        if (isHtml) {
            Charset charset = parseCharset(rawCt);
            String html = new String(bodyBytes, charset);
            String baseTag = "<base href=\"" + escapeHtmlAttr(targetUrl) + "\">";
            html = injectBaseTag(html, baseTag);
            finalBytes = html.getBytes(charset);
            bodyString = html;
        }

        if (isJson) {
            if (bodyString == null) {
                bodyString = new String(finalBytes, StandardCharsets.UTF_8);
            }
            String json = buildJsonMetadata(targetUrl, statusCode, rawCt, finalBytes.length, bodyString);
            RouterHandler.send(ex, 200, "application/json; charset=utf-8", json);
        } else {
            // Forward Server header neu co
            response.headers().firstValue("Server").ifPresent(srv -> ex.getResponseHeaders().set("Server", srv));
            ex.getResponseHeaders().set("Content-Type", rawCt);
            long len = (finalBytes.length == 0) ? -1 : finalBytes.length;
            ex.sendResponseHeaders(statusCode, len);
            if (finalBytes.length > 0) {
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(finalBytes);
                }
            } else {
                ex.close();
            }
            ServerLogger.log("GET", ex.getRequestURI().getPath(), statusCode, finalBytes.length,
                ex.getRemoteAddress() != null ? ex.getRemoteAddress().toString() : "unknown");
        }
    }

    public static String extractUrlParam(String query) {
        if (query == null) return null;
        int urlStart = -1;
        if (query.startsWith("url=")) {
            urlStart = 4;
        } else {
            int idx = query.indexOf("&url=");
            if (idx >= 0) {
                urlStart = idx + 5;
            }
        }
        if (urlStart == -1) return null;

        String candidate = query.substring(urlStart);
        int fmtIdx = candidate.indexOf("&format=");
        if (fmtIdx >= 0) {
            candidate = candidate.substring(0, fmtIdx);
        }
        return RouterHandler.urlDecodeOr(candidate, candidate);
    }

    public static String injectBaseTag(String html, String baseTag) {
        if (html == null) return baseTag;
        // 1. Tim the <head...>
        Matcher headMatcher = HEAD_PATTERN.matcher(html);
        if (headMatcher.find()) {
            int idx = headMatcher.end();
            return html.substring(0, idx) + baseTag + html.substring(idx);
        }
        // 2. Tim the <html...>
        Matcher htmlMatcher = HTML_PATTERN.matcher(html);
        if (htmlMatcher.find()) {
            int idx = htmlMatcher.end();
            return html.substring(0, idx) + "<head>" + baseTag + "</head>" + html.substring(idx);
        }
        // 3. Tim the <!doctype ...>
        Matcher doctypeMatcher = DOCTYPE_PATTERN.matcher(html);
        if (doctypeMatcher.find()) {
            int idx = doctypeMatcher.end();
            return html.substring(0, idx) + "<head>" + baseTag + "</head>" + html.substring(idx);
        }
        // 4. Fallback: prepend
        return "<head>" + baseTag + "</head>" + html;
    }

    public static Charset parseCharset(String contentType) {
        if (contentType == null) return StandardCharsets.UTF_8;
        try {
            String lower = contentType.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf("charset=");
            if (idx >= 0) {
                String cs = contentType.substring(idx + 8).trim();
                int semi = cs.indexOf(';');
                if (semi >= 0) cs = cs.substring(0, semi).trim();
                cs = cs.replace("\"", "").replace("'", "").trim();
                return Charset.forName(cs);
            }
        } catch (Exception ignored) {}
        return StandardCharsets.UTF_8;
    }

    public static String escapeHtmlAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static String buildJsonMetadata(String targetUrl, int status, String contentType, long contentLength, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"targetUrl\":\"").append(escapeJson(targetUrl)).append("\",");
        sb.append("\"status\":").append(status).append(",");
        sb.append("\"contentType\":\"").append(escapeJson(contentType)).append("\",");
        sb.append("\"contentLength\":").append(contentLength).append(",");
        sb.append("\"body\":\"").append(escapeJson(body)).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
