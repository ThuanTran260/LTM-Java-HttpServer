package server;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import service.StudentService;
import model.Student;

public class RouterHandler implements HttpHandler {
    private static final StudentService SVC = new StudentService();
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void handle(HttpExchange ex) throws IOException {
        // PATH lay tu getPath(), QUERY STRING lay tu getRawQuery() (giu nguyen dang %-encoded)
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getRawQuery();
        String method = ex.getRequestMethod();
        try {
            // 1. Static file: moi GET khong thuoc /api va /iis deu thu phuc vu tu wwwroot/
            if (method.equals("GET") && !path.startsWith("/api/") && !path.startsWith("/iis/")) {
                if (StaticFileHandler.tryServe(ex, path)) return;
                if (path.equals("/")) {
                    send(ex, 200, "text/html; charset=utf-8", "<h1>HTTP Server dang hoat dong!</h1><p><a href='/api/time'>/api/time</a> | <a href='/api/students'>/api/students</a> | <a href='/iis/'>/iis/</a></p>");
                    return;
                }
            }
            // 2. GET /api/time
            if (method.equals("GET") && path.equals("/api/time")) {
                String json = "{\"time\":\"" + LocalDateTime.now().format(HHMMSS) + "\"}";
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
            // 4. CRUD /api/students (ho tro query string de loc: ?name= &age= )
            if (path.equals("/api/students")) {
                if (method.equals("GET")) {
                    String nameFilter = param(query, "name");
                    String ageFilter = param(query, "age");
                    send(ex, 200, "application/json", filterJson(nameFilter, ageFilter));
                    return;
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

    // Loc danh sach sinh vien theo tham so tren Query String
    private String filterJson(String nameFilter, String ageFilter) {
        List<Student> all = SVC.list();
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (Student s : all) {
            if (nameFilter != null && (s.name == null || !s.name.toLowerCase().contains(nameFilter.toLowerCase()))) continue;
            if (ageFilter != null) {
                try { if (s.age != Integer.parseInt(ageFilter)) continue; }
                catch (NumberFormatException e) { continue; }
            }
            if (count > 0) sb.append(",");
            sb.append(s.toJson());
            count++;
        }
        return sb.append("]").toString();
    }

    // Lay 1 tham so tu query string "name=An&age=20", tu giai ma UTF-8
    static String param(String query, String key) {
        if (query == null || query.isEmpty()) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            if (pair.substring(0, eq).equals(key)) {
                try { return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8); }
                catch (Exception e) { return pair.substring(eq + 1); }
            }
        }
        return null;
    }

    public static void send(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        ServerLogger.log(ex.getRequestMethod(), ex.getRequestURI().getPath(), status, data.length, ex.getRemoteAddress().toString());
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
