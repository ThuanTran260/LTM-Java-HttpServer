package client;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logic goi WebFetch cua Java Server: GET <server>/fetch?url=<target>[&format=json].
 * Khong dung Swing de CrossTestClient test duoc ma khong can man hinh.
 * - Che do raw : status / Content-Type lay tu HTTP response cua server, body la noi dung trang dich.
 * - Che do json: server tra {targetUrl,status,contentType,contentLength,body}; lop nay tach cac truong do.
 * Moi loi mang duoc tra ve trong Result.error (khong nem exception) de giao dien chi viec hien thi.
 */
public final class WebFetchService {
    public static final String DEFAULT_SERVER = "http://localhost:8080";

    // Timeout request lon hon timeout 10s cua WebFetchHandler de nhan duoc 502 cua server thay vi tu ngat truoc
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    private static final Pattern CHARSET_IN_HEADER = Pattern.compile("(?i)charset\\s*=\\s*\"?([^\\s;\"]+)");
    private static final Pattern CHARSET_IN_META = Pattern.compile("(?i)<meta\\s+[^>]*charset=[\"']?([a-zA-Z0-9_-]+)");

    /** Ket qua mot lan fetch. status = -1 va error != null khi khong nhan duoc phan hoi tu server. */
    public record Result(String requestUrl, boolean jsonMode, int status, String contentType,
                         long contentLength, String body, String rawResponse, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    private final String serverBase;

    public WebFetchService() {
        this(DEFAULT_SERVER);
    }

    public WebFetchService(String serverBase) {
        String s = serverBase == null ? "" : serverBase.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        this.serverBase = s.isEmpty() ? DEFAULT_SERVER : s;
    }

    public String getServerBase() {
        return serverBase;
    }

    public String buildRequestUrl(String targetUrl, boolean json) {
        return serverBase + "/fetch?" + (json ? "format=json&" : "")
            + "url=" + URLEncoder.encode(targetUrl, StandardCharsets.UTF_8);
    }

    public Result fetch(String targetUrl, boolean json) {
        String target = targetUrl == null ? "" : targetUrl.trim();
        if (target.isEmpty()) {
            return error(null, json, "URL dang de trong. Hay nhap URL http:// hoac https://");
        }
        String requestUrl = buildRequestUrl(target, json);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(requestUrl)).timeout(REQUEST_TIMEOUT).GET().build();
        } catch (IllegalArgumentException e) {
            return error(requestUrl, json, "Dia chi WebFetch server khong hop le: " + serverBase);
        }

        HttpResponse<byte[]> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            return error(requestUrl, json, "Het thoi gian cho (" + REQUEST_TIMEOUT.toSeconds() + "s) khi goi " + serverBase);
        } catch (ConnectException e) {
            return error(requestUrl, json, "Khong ket noi duoc toi WebFetch server " + serverBase + " - hay chay run_server.bat");
        } catch (IOException e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return error(requestUrl, json, "Loi mang khi goi " + serverBase + ": " + detail);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(requestUrl, json, "Yeu cau bi huy giua chung");
        }

        int httpStatus = response.statusCode();
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        byte[] bytes = response.body() == null ? new byte[0] : response.body();

        // Json mode: chi parse khi server that su tra JSON (loi 400 kieu text/plain duoc hien nhu raw)
        if (json && contentType.toLowerCase(Locale.ROOT).contains("application/json")) {
            String rawJson = new String(bytes, charsetOf(contentType, bytes));
            try {
                Map<String, String> m = parseFlatJson(rawJson);
                int status = Integer.parseInt(m.get("status"));
                long length = Long.parseLong(m.get("contentLength"));
                String parsedType = m.getOrDefault("contentType", "");
                // Server json mode giai ma moi body thanh chuoi, nen body nhi phan phai thay bang placeholder
                String parsedBody = isTextual(parsedType)
                    ? m.getOrDefault("body", "")
                    : binaryPlaceholder(parsedType, length);
                return new Result(requestUrl, true, status, parsedType, length, parsedBody, rawJson, null);
            } catch (RuntimeException e) {
                return error(requestUrl, true, "Phan hoi JSON cua server khong hop le: " + e.getMessage());
            }
        }

        String body;
        if (isTextual(contentType)) {
            body = new String(bytes, charsetOf(contentType, bytes));
        } else {
            body = binaryPlaceholder(contentType, bytes.length);
        }
        return new Result(requestUrl, json, httpStatus, contentType, bytes.length, body, body, null);
    }

    private static Result error(String requestUrl, boolean json, String message) {
        return new Result(requestUrl, json, -1, "", 0, "", "", message);
    }

    private static String binaryPlaceholder(String contentType, long length) {
        return "[Noi dung nhi phan: " + contentType + ", " + length + " bytes - khong hien thi duoc dang text]";
    }

    public static boolean isHtml(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("html");
    }

    public static boolean isTextual(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.startsWith("text/") || ct.contains("json") || ct.contains("xml")
            || ct.contains("javascript") || ct.contains("html");
    }

    public static String suggestFileName(Result r) {
        if (r.jsonMode() && r.rawResponse().startsWith("{")) {
            return "response.json";
        }
        return isHtml(r.contentType()) ? "source.html" : "source.txt";
    }

    /** Charset: header Content-Type -> the meta charset (chi voi HTML) -> UTF-8. */
    static Charset charsetOf(String contentType, byte[] bytes) {
        if (contentType != null) {
            Matcher m = CHARSET_IN_HEADER.matcher(contentType);
            if (m.find()) {
                try {
                    return Charset.forName(m.group(1));
                } catch (RuntimeException ignored) {
                    // charset la hoac khong ho tro -> thu cach tiep theo
                }
            }
        }
        if (bytes != null && bytes.length > 0 && isHtml(contentType)) {
            String head = new String(bytes, 0, Math.min(bytes.length, 2048), StandardCharsets.ISO_8859_1);
            Matcher m = CHARSET_IN_META.matcher(head);
            if (m.find()) {
                try {
                    return Charset.forName(m.group(1));
                } catch (RuntimeException ignored) {
                    // charset la hoac khong ho tro -> mac dinh UTF-8
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Parse JSON phang {"key":"chuoi" | so, ...} - dung dinh dang cua WebFetchHandler.buildJsonMetadata.
     * Gia tri chuoi duoc giai ma escape; gia tri so tra ve dang chuoi. Nem IllegalArgumentException neu sai cu phap.
     */
    static Map<String, String> parseFlatJson(String json) {
        return new FlatJsonReader(json).read();
    }

    private static final class FlatJsonReader {
        private final String s;
        private int i = 0;

        FlatJsonReader(String s) {
            this.s = s == null ? "" : s;
        }

        Map<String, String> read() {
            Map<String, String> out = new LinkedHashMap<>();
            skipWs();
            expect('{');
            skipWs();
            if (peek() == '}') {
                i++;
                return out;
            }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                skipWs();
                String value;
                if (peek() == '"') {
                    value = readString();
                } else {
                    int start = i;
                    while (i < s.length() && s.charAt(i) != ',' && s.charAt(i) != '}') {
                        i++;
                    }
                    value = s.substring(start, i).trim();
                }
                out.put(key, value);
                skipWs();
                if (i >= s.length()) {
                    throw new IllegalArgumentException("JSON bi cat cut");
                }
                char c = s.charAt(i++);
                if (c == '}') {
                    return out;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Mong doi ',' hoac '}' tai vi tri " + (i - 1));
                }
            }
        }

        private char peek() {
            return i < s.length() ? s.charAt(i) : '\0';
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) {
                throw new IllegalArgumentException("Mong doi '" + c + "' tai vi tri " + i);
            }
            i++;
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (i >= s.length()) {
                    break;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) {
                            throw new IllegalArgumentException("Chuoi unicode escape bi cat cut");
                        }
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("Escape khong hop le: \\" + e);
                }
            }
            throw new IllegalArgumentException("Chuoi JSON khong dong");
        }
    }
}
