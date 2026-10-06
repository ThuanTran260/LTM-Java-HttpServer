package server;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
public class StaticFileHandler {
    public static boolean tryServe(HttpExchange ex, String path) throws IOException {
        String rel;
        if (path.equals("/")) rel = "index.html";
        else if (path.startsWith("/") && !hasDotDot(path) && contentTypeFor(path.toLowerCase(java.util.Locale.ROOT)) != null) rel = path.substring(1);
        else return false;
        Path file = Paths.get("wwwroot", rel).toAbsolutePath().normalize();
        Path root = Paths.get("wwwroot").toAbsolutePath().normalize();
        if (!file.startsWith(root) || !Files.exists(file) || Files.isDirectory(file)) return false;
        byte[] data = Files.readAllBytes(file);
        String ct = contentTypeFor(rel);
        ex.getResponseHeaders().set("Content-Type", ct);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        ServerLogger.log(ex.getRequestMethod(), path, 200, data.length, ex.getRemoteAddress().toString());
        return true;
    }
    static boolean hasDotDot(String path) {
        if (path.contains("..")) return true;
        try { return URLDecoder.decode(path, StandardCharsets.UTF_8).contains(".."); }
        catch (IllegalArgumentException e) { return true; }
    }
    public static String contentTypeFor(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".json")) return "application/json";
        return null;
    }
}
