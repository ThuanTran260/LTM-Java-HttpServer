package server;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
public class StaticFileHandler {
    public static boolean tryServe(HttpExchange ex, String path) throws IOException {
        return tryServe(ex, path, Paths.get("wwwroot"));
    }

    public static boolean tryServe(HttpExchange ex, String path, Path webRoot) throws IOException {
        String rel;
        if (path.equals("/")) rel = "index.html";
        else if (path.startsWith("/") && !hasDotDot(path) && contentTypeFor(path) != null) rel = path.substring(1);
        else return false;

        Path file;
        try {
            rel = RouterHandler.urlDecodeOr(rel, rel);
            file = SafePaths.resolveSafe(webRoot, rel);
            if (!Files.exists(file) || Files.isDirectory(file)) return false;
        } catch (SecurityException | IllegalArgumentException e) {
            return false;
        }

        long size = Files.size(file);
        String ct = contentTypeFor(rel);
        ex.getResponseHeaders().set("Content-Type", ct);
        long len = (size == 0) ? -1 : size;
        ex.sendResponseHeaders(200, len);
        if (size > 0) {
            try (OutputStream os = ex.getResponseBody();
                 InputStream is = Files.newInputStream(file)) {
                is.transferTo(os);
            }
        } else {
            ex.close();
        }
        ServerLogger.log(ex.getRequestMethod(), path, 200, (int) Math.min(size, Integer.MAX_VALUE),
            ex.getRemoteAddress() != null ? ex.getRemoteAddress().toString() : "unknown");
        return true;
    }
    static boolean hasDotDot(String path) {
        if (path.contains("..") || path.contains("\\")) return true;
        String d = RouterHandler.urlDecodeOr(path, null);
        return d == null || d.contains("..") || d.contains("\\");
    }
    public static String contentTypeFor(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".txt")) return "text/plain; charset=utf-8";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".ico")) return "image/x-icon";
        return null;
    }
}
