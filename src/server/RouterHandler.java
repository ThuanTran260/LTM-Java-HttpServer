package server;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import service.StudentService;
import model.Student;
public class RouterHandler implements HttpHandler {
    private static final StudentService SVC = new StudentService();
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        try {
            // 1. Static wwwroot (index.html, style.css, sample.png, ...)
            if (method.equals("GET") && StaticFileHandler.tryServe(ex, path)) return;
            // fallback neu chua co wwwroot
            if (method.equals("GET") && path.equals("/")) {
                send(ex, 200, "text/html; charset=utf-8", "<h1>HTTP Server dang hoat dong!</h1><p><a href='/api/time'>/api/time</a> | <a href='/api/students'>/api/students</a> | <a href='/iis/'>/iis/</a></p>");
                return;
            }
            // 2. GET /api/time
            if (method.equals("GET") && path.equals("/api/time")) {
                String json = "{\"time\":\"" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\"}";
                send(ex, 200, "application/json", json);
                return;
            }
            // 3. POST /api/message
            if (method.equals("POST") && path.equals("/api/message")) {
                String ct = ex.getRequestHeaders().getFirst("Content-Type");
                if (ct == null || !ct.startsWith("application/json")) {
                    send(ex, 415, "text/plain; charset=utf-8", "415 Unsupported Media Type");
                    return;
                }
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (body == null || body.isBlank()) {
                    send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request: empty body");
                    return;
                }
                send(ex, 201, "application/json", "{\"message\":\"Da nhan du lieu\"}");
                return;
            }
            // 4. CRUD /api/students
            if (path.equals("/api/students")) {
                if (method.equals("GET")) {
                    String qid = queryParam(ex.getRequestURI().getQuery(), "id");
                    if (qid != null) {
                        int qidInt;
                        try { qidInt = Integer.parseInt(qid); }
                        catch (NumberFormatException e) { send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request"); return; }
                        Student s = SVC.get(qidInt);
                        if (s == null) send(ex, 404, "text/plain; charset=utf-8", "404 Not Found");
                        else send(ex, 200, "application/json", s.toJson());
                        return;
                    }
                    send(ex, 200, "application/json", SVC.listJson()); return;
                }
                if (method.equals("POST")) {
                    String ct = ex.getRequestHeaders().getFirst("Content-Type");
                    if (ct == null || !ct.startsWith("application/json")) {
                        send(ex, 415, "text/plain; charset=utf-8", "415 Unsupported Media Type"); return;
                    }
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String name = parseJsonString(body, "name");
                    Integer age = parseJsonInt(body, "age");
                    if (name == null || age == null) { send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request"); return; }
                    Student s = SVC.add(name, age);
                    send(ex, 201, "application/json", s.toJson());
                    return;
                }
            }
            if (path.startsWith("/api/students/")) {
                int id;
                try { id = Integer.parseInt(path.substring("/api/students/".length())); }
                catch (NumberFormatException e) { send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request"); return; }
                if (method.equals("GET")) {
                    Student s = SVC.get(id);
                    if (s == null) send(ex, 404, "text/plain; charset=utf-8", "404 Not Found");
                    else send(ex, 200, "application/json", s.toJson());
                    return;
                }
                if (method.equals("PUT")) {
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String name = parseJsonString(body, "name");
                    Integer age = parseJsonInt(body, "age");
                    if (name == null || age == null) { send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request"); return; }
                    if (!SVC.update(id, name, age)) send(ex, 404, "text/plain; charset=utf-8", "404 Not Found");
                    else send(ex, 200, "application/json", SVC.get(id).toJson());
                    return;
                }
                if (method.equals("DELETE")) {
                    if (!SVC.delete(id)) send(ex, 404, "text/plain; charset=utf-8", "404 Not Found");
                    else send(ex, 200, "application/json", "{\"message\":\"Deleted\"}");
                    return;
                }
            }
            send(ex, 404, "text/plain; charset=utf-8", "404 Not Found");
        } catch (Exception e) {
            try { send(ex, 500, "text/plain; charset=utf-8", "500 Internal Server Error"); }
            catch (Exception ignored) {}
        }
    }
    public static void send(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        ServerLogger.log(ex.getRequestMethod(), ex.getRequestURI().getPath(), status, data.length, ex.getRemoteAddress().toString());
    }
    static String queryParam(String query, String key) {
        if (query == null) return null;
        for (String p : query.split("&")) {
            if (p.startsWith(key + "=")) return p.substring(key.length() + 1);
        }
        return null;
    }
    static String parseJsonString(String json, String key) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return null;
        int c = json.indexOf(':', i);
        if (c < 0) return null;
        int q1 = json.indexOf('"', c);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }
    static Integer parseJsonInt(String json, String key) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return null;
        int c = json.indexOf(':', i);
        if (c < 0) return null;
        int s = c + 1;
        while (s < json.length() && (json.charAt(s) == ' ' || json.charAt(s) == '"')) s++;
        int e = s;
        while (e < json.length() && (Character.isDigit(json.charAt(e)) || json.charAt(e) == '-')) e++;
        try { return Integer.parseInt(json.substring(s, e)); } catch (Exception ex) { return null; }
    }
}
