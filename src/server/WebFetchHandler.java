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
import java.util.zip.InflaterInputStream;

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

    public static class FetchParams {
        public final String targetUrl;
        public final boolean isJson;

        public FetchParams(String targetUrl, boolean isJson) {
            this.targetUrl = targetUrl;
            this.isJson = isJson;
        }
    }

    public static FetchParams extractParams(String query) {
        if (query == null || query.isBlank()) return null;

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        int urlValStart = -1;
        int urlKeyStart = -1;

        if (lowerQuery.startsWith("url=")) {
            urlKeyStart = 0;
            urlValStart = 4;
        } else {
            int idx = lowerQuery.indexOf("&url=");
            if (idx >= 0) {
                urlKeyStart = idx + 1;
                urlValStart = idx + 5;
            }
        }
        if (urlValStart == -1) return null;

        // Kiem tra format co nam TRUOC url= khong (vi du: format=json&url=...)
        boolean isJson = false;
        if (urlKeyStart > 0) {
            String prefix = query.substring(0, urlKeyStart - 1);
            for (String p : prefix.split("&")) {
                int eq = p.indexOf('=');
                if (eq > 0) {
                    String k = p.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                    String v = p.substring(eq + 1).trim().toLowerCase(Locale.ROOT);
                    if ("format".equals(k) && "json".equals(v)) {
                        isJson = true;
                    }
                }
            }
        }

        String candidate = query.substring(urlValStart);

        // Neu format chua xac dinh truoc url, kiem tra xem co nam o cuoi candidate khong
        if (!isJson) {
            String lowerCandidate = candidate.toLowerCase(Locale.ROOT);
            int lastFmt = lowerCandidate.lastIndexOf("&format=");
            if (lastFmt >= 0) {
                String fmtVal = lowerCandidate.substring(lastFmt + 8);
                // format cua fetcher chi chap nhan "json", "raw", hoac rong "" (tu form HTML)
                boolean isPercentEncoded = lowerCandidate.startsWith("http%3a") || lowerCandidate.startsWith("https%3a");
                if (isPercentEncoded || fmtVal.equals("json") || fmtVal.equals("raw") || fmtVal.isEmpty()
                    || fmtVal.startsWith("json&") || fmtVal.startsWith("raw&") || fmtVal.startsWith("&")) {
                    if (fmtVal.equals("json") || fmtVal.startsWith("json&")) {
                        isJson = true;
                    }
                    candidate = candidate.substring(0, lastFmt);
                }
            }
        }

        // Giai ma percent-decode neu toan bo url bi encode (vi du: tu form HTML hoac client)
        String finalUrl = candidate;
        String lowerCandidate = candidate.toLowerCase(Locale.ROOT);
        if (lowerCandidate.startsWith("http%3a") || lowerCandidate.startsWith("https%3a")
            || lowerCandidate.startsWith("file%3a") || lowerCandidate.startsWith("ftp%3a")
            || lowerCandidate.startsWith("javascript%3a") || lowerCandidate.startsWith("data%3a")) {
            finalUrl = RouterHandler.urlDecodeOr(candidate, candidate);
        } else if (finalUrl.contains("%23") || finalUrl.contains("%23".toLowerCase(Locale.ROOT))) {
            finalUrl = finalUrl.replace("%23", "#").replace("%23".toLowerCase(Locale.ROOT), "#");
        }

        return new FetchParams(finalUrl, isJson);
    }

    public static String extractUrlParam(String query) {
        FetchParams p = extractParams(query);
        return p != null ? p.targetUrl : null;
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

        FetchParams params = extractParams(query);
        if (params == null || params.targetUrl == null || params.targetUrl.isBlank()) {
            RouterHandler.send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request: missing url parameter");
            return;
        }

        String targetUrl = params.targetUrl.trim();

        // Kiem tra hop le cua URL & Scheme (Q3)
        URI uri = parseTargetUri(targetUrl);
        if (uri == null) {
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

        fetchAndRespond(ex, uri, targetUrl, params.isJson);
    }

    private static final Pattern URL_PARSER = Pattern.compile("(?i)^(https?)://([^/?#]+)(/[^?#]*)?(\\?[^#]*)?(#.*)?$");

    public static URI parseTargetUri(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) return null;
        String s = targetUrl.trim();
        try {
            return URI.create(s);
        } catch (Exception e) {
            try {
                Matcher m = URL_PARSER.matcher(s);
                if (m.matches()) {
                    String scheme = m.group(1);
                    String authority = m.group(2);
                    String path = m.group(3) != null ? m.group(3) : "";
                    String query = m.group(4) != null ? m.group(4).substring(1) : null;
                    return new URI(scheme, authority, path, query, null);
                }
            } catch (Exception ignored) {}
            return null;
        }
    }

    private void fetchAndRespond(HttpExchange ex, URI uri, String targetUrl, boolean isJson) throws IOException {
        // HTTP client khong the gui fragment (#) len may chu goc; strip fragment de tao HttpRequest hop le
        URI requestUri = uri;
        if (requestUri.getFragment() != null) {
            try {
                requestUri = new URI(requestUri.getScheme(), requestUri.getAuthority(),
                    requestUri.getPath(), requestUri.getQuery(), null);
            } catch (Exception ignored) {}
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(requestUri)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Java-HttpServer-WebFetcher/1.0")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "vi,en-US;q=0.9,en;q=0.8")
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

        // Giai nen GZIP / Deflate neu upstream tra ve nen
        String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("");
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            try (ByteArrayInputStream bis = new ByteArrayInputStream(bodyBytes);
                 GZIPInputStream gis = new GZIPInputStream(bis)) {
                bodyBytes = gis.readAllBytes();
            } catch (Exception ignored) {}
        } else if ("deflate".equalsIgnoreCase(contentEncoding)) {
            try (ByteArrayInputStream bis = new ByteArrayInputStream(bodyBytes);
                 InflaterInputStream iis = new InflaterInputStream(bis)) {
                bodyBytes = iis.readAllBytes();
            } catch (Exception ignored) {}
        }

        boolean isHtml = rawCt.toLowerCase(Locale.ROOT).contains("text/html");
        byte[] finalBytes = bodyBytes;
        String bodyString = null;
        Charset charset = parseCharset(rawCt, bodyBytes);

        if (isHtml) {
            String html = new String(bodyBytes, charset);
            String baseTag = "<base href=\"" + escapeHtmlAttr(targetUrl) + "\">";
            html = injectBaseTag(html, baseTag);
            finalBytes = html.getBytes(charset);
            bodyString = html;
        }

        if (isJson) {
            if (bodyString == null) {
                bodyString = new String(finalBytes, charset);
            }
            String json = buildJsonMetadata(targetUrl, statusCode, rawCt, finalBytes.length, bodyString);
            RouterHandler.send(ex, 200, "application/json; charset=utf-8", json);
        } else {
            // Forward Server header neu co
            response.headers().firstValue("Server").ifPresent(srv -> ex.getResponseHeaders().set("Server", srv));
            ex.getResponseHeaders().set("Content-Type", rawCt);
            long len;
            if (statusCode == 204 || statusCode == 304 || finalBytes.length == 0) {
                len = -1;
            } else {
                len = finalBytes.length;
            }
            ex.sendResponseHeaders(statusCode, len);
            if (len > 0) {
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

    public static String injectBaseTag(String html, String baseTag) {
        if (html == null) return baseTag;
        // 1. Tim the <head...> khong nam trong comment HTML
        Matcher headMatcher = HEAD_PATTERN.matcher(html);
        while (headMatcher.find()) {
            int start = headMatcher.start();
            if (isInsideComment(html, start)) {
                continue;
            }
            int idx = headMatcher.end();
            return html.substring(0, idx) + baseTag + html.substring(idx);
        }
        // 2. Tim the <html...> khong nam trong comment HTML
        Matcher htmlMatcher = HTML_PATTERN.matcher(html);
        while (htmlMatcher.find()) {
            int start = htmlMatcher.start();
            if (isInsideComment(html, start)) {
                continue;
            }
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

    private static boolean isInsideComment(String html, int pos) {
        int lastCommentOpen = html.lastIndexOf("<!--", pos);
        if (lastCommentOpen != -1) {
            int lastCommentClose = html.indexOf("-->", lastCommentOpen);
            return lastCommentClose == -1 || lastCommentClose > pos;
        }
        return false;
    }

    public static Charset parseCharset(String contentType) {
        return parseCharset(contentType, null);
    }

    public static Charset parseCharset(String contentType, byte[] bodyBytes) {
        if (contentType != null) {
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
        }
        if (bodyBytes != null && bodyBytes.length > 0) {
            try {
                int checkLen = Math.min(bodyBytes.length, 2048);
                String headPreview = new String(bodyBytes, 0, checkLen, StandardCharsets.ISO_8859_1);
                Matcher m = Pattern.compile("(?i)<meta\\s+[^>]*charset=[\"']?([a-zA-Z0-9_-]+)").matcher(headPreview);
                if (m.find()) {
                    return Charset.forName(m.group(1));
                }
            } catch (Exception ignored) {}
        }
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
        StringBuilder sb = new StringBuilder((int) (s.length() * 1.1) + 16);
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
                case '\u2028': sb.append("\\u2028"); break;
                case '\u2029': sb.append("\\u2029"); break;
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
