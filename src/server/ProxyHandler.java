package server;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
// Proxy den IIS that :80; tra 502 neu IIS chua chay hoac khong ket noi duoc.
public class ProxyHandler implements HttpHandler {
    private static final String REAL_IIS = "http://localhost:80";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String rawPath = ex.getRequestURI().getRawPath();
        String rawQuery = ex.getRequestURI().getRawQuery();
        String upstreamPath;
        if (rawPath == null || rawPath.equals("/iis") || rawPath.equals("/iis/")) upstreamPath = "/";
        else if (rawPath.startsWith("/iis/")) upstreamPath = rawPath.substring(4);
        else { RouterHandler.sendNotFound(ex); return; }
        if (rawQuery != null && !rawQuery.isEmpty()) upstreamPath += "?" + rawQuery;
        String method = ex.getRequestMethod();
        byte[] reqBody = ex.getRequestBody().readAllBytes();
        if (forward(ex, method, REAL_IIS + upstreamPath, reqBody)) return;
        RouterHandler.send(ex, 502, "text/plain; charset=utf-8",
            "502 Bad Gateway: khong ket noi duoc IIS :80. Hay bat IIS va kiem tra http://localhost/.");
    }
    private boolean forward(HttpExchange ex, String method, String url, byte[] reqBody) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2));
            // copy Content-Type neu co
            String ct = ex.getRequestHeaders().getFirst("Content-Type");
            if (ct != null) b.header("Content-Type", ct);
            if (method.equals("GET") || method.equals("DELETE")) b.method(method, HttpRequest.BodyPublishers.noBody());
            else b.method(method, HttpRequest.BodyPublishers.ofByteArray(reqBody == null ? new byte[0] : reqBody));
            HttpResponse<byte[]> r = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            String resCt = r.headers().firstValue("Content-Type").orElse("text/html; charset=utf-8");
            byte[] body = r.body() == null ? new byte[0] : r.body();
            ex.getResponseHeaders().set("Content-Type", resCt);
            r.headers().firstValue("Server").ifPresent(srv -> ex.getResponseHeaders().set("Server", srv));
            UpstreamContentPolicy.apply(ex);
            long len = body.length == 0 ? -1 : body.length;
            ex.sendResponseHeaders(r.statusCode(), len);
            if (body.length > 0) {
                try (var os = ex.getResponseBody()) { os.write(body); }
            } else {
                ex.close();
            }
            System.out.println("[PROXY FORWARD] " + method + " " + ex.getRequestURI().getPath() + " -> " + url + " (IIS :80) -> " + r.statusCode());
            ServerLogger.log(method, ex.getRequestURI().getPath(), r.statusCode(), body.length,
                ex.getRemoteAddress() != null ? ex.getRemoteAddress().toString() : "unknown");
            return true;
        } catch (Exception e) {
            System.out.println("[PROXY MISS] " + url + " : " + e.getMessage());
            return false;
        }
    }
}
