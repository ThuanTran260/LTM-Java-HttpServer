package server;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

// Phuc vu bat ky file tinh nao trong wwwroot/ (index.html, css, png, json, txt).
// Dam bao khong cho ra ngoai thu muc wwwroot (chong path traversal "../").
public class StaticFileHandler {
    private static final Path ROOT = Paths.get("wwwroot").toAbsolutePath().normalize();

    public static boolean tryServe(HttpExchange ex, String path) throws IOException {
        String rel = resolveRelative(path);
        if (rel == null) return false;
        Path file = ROOT.resolve(rel).normalize();
        // Chiang an toan: file phai nam trong wwwroot
        if (!file.startsWith(ROOT) || !Files.isRegularFile(file)) return false;
        byte[] data = Files.readAllBytes(file);
        String ct = contentTypeFor(rel);
        ex.getResponseHeaders().set("Content-Type", ct);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        ServerLogger.log(ex.getRequestMethod(), path, 200, data.length, ex.getRemoteAddress().toString());
        return true;
    }

    // "/" -> "index.html" ; "/index.html" -> "index.html" ; "/style.css" -> "style.css"
    // "/../secret" -> null (tu choi)
    private static String resolveRelative(String path) {
        if (path == null || path.isEmpty()) return "index.html";
        String p = path.replace('\\', '/');
        if (p.equals("/")) return "index.html";
        while (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty()) return "index.html";
        if (p.contains("..") || p.startsWith(".")) return null;
        return p;
    }

    public static String contentTypeFor(String name) {
        String n = name.toLowerCase();
        int dot = n.lastIndexOf('.');
        String ext = dot < 0 ? "" : n.substring(dot);
        switch (ext) {
            case ".html": case ".htm": return "text/html; charset=utf-8";
            case ".css":  return "text/css; charset=utf-8";
            case ".js":   return "text/javascript; charset=utf-8";
            case ".json": return "application/json";
            case ".png":  return "image/png";
            case ".jpg":  case ".jpeg": return "image/jpeg";
            case ".gif":  return "image/gif";
            case ".svg":  return "image/svg+xml";
            case ".ico":  return "image/x-icon";
            case ".txt":  return "text/plain; charset=utf-8";
            default:      return "application/octet-stream";
        }
    }
}
