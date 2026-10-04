package server;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
public class StaticFileHandler {
    public static boolean tryServe(HttpExchange ex, String path) throws IOException {
        String rel;
        if (path.equals("/")) rel = "index.html";
        else if (path.equals("/style.css")) rel = "style.css";
        else return false;
        Path file = Paths.get("wwwroot", rel).toAbsolutePath().normalize();
        Path root = Paths.get("wwwroot").toAbsolutePath().normalize();
        if (!file.startsWith(root) || !Files.exists(file)) return false;
        byte[] data = Files.readAllBytes(file);
        String ct = rel.endsWith(".css") ? "text/css; charset=utf-8" : "text/html; charset=utf-8";
        ex.getResponseHeaders().set("Content-Type", ct);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        ServerLogger.log(ex.getRequestMethod(), path, 200, data.length, ex.getRemoteAddress().toString());
        return true;
    }
    public static String contentTypeFor(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".json")) return "application/json";
        return "text/plain; charset=utf-8";
    }
}
