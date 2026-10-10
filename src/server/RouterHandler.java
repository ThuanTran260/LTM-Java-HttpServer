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

    public static StudentService getStudentService() {
        return SVC;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String rawPath = ex.getRequestURI().getPath();
        String path = (rawPath == null || rawPath.isEmpty()) ? "/" : rawPath;

        // Ho tro ca duong dan /students tuong thich Slide 10 (GET /students/10)
        if (path.equals("/students")) path = "/api/students";
        else if (path.equals("/students/")) path = "/api/students/";
        else if (path.startsWith("/students/")) path = "/api/students/" + path.substring("/students/".length());
        else if (path.equals("/time") || path.equals("/time/")) path = "/api/time";
        else if (path.equals("/message") || path.equals("/message/")) path = "/api/message";

        String query = ex.getRequestURI().getRawQuery();
        String method = ex.getRequestMethod();

        try {
            // 1. Static files tu wwwroot (index.html, style.css, sample.png, ...)
            if (method.equals("GET") && StaticFileHandler.tryServe(ex, path)) return;

            // Fallback neu wwwroot/index.html khong ton tai
            if (method.equals("GET") && path.equals("/")) {
                send(ex, 200, "text/html; charset=utf-8", "<h1>HTTP Server dang hoat dong!</h1><p><a href='/api/time'>/api/time</a> | <a href='/api/students'>/api/students</a> | <a href='/iis/'>/iis/</a></p>");
                return;
            }

            // 2. GET /api/time
            if (path.equals("/api/time") || path.equals("/api/time/")) {
                if (!method.equals("GET")) {
                    ex.getResponseHeaders().set("Allow", "GET");
                    send(ex, 405, "text/plain; charset=utf-8", "405 Method Not Allowed");
                    return;
                }
                String json = "{\"time\":\"" + LocalDateTime.now().format(HHMMSS) + "\"}";
                send(ex, 200, "application/json", json);
                return;
            }

            // 3. POST /api/message
            if (path.equals("/api/message") || path.equals("/api/message/")) {
                if (!method.equals("POST")) {
                    ex.getResponseHeaders().set("Allow", "POST");
                    send(ex, 405, "text/plain; charset=utf-8", "405 Method Not Allowed");
                    return;
                }
                String ct = ex.getRequestHeaders().getFirst("Content-Type");
                if (ct == null || !ct.toLowerCase().startsWith("application/json")) {
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

            // 4. CRUD /api/students (?id= tra 1 SV dung mau slide, ?name=/ ?age= loc danh sach)
            if (path.equals("/api/students") || path.equals("/api/students/")) {
                if (method.equals("GET")) {
                    String qid = queryParam(query, "id");
                    if (qid != null) {
                        if (qid.isBlank()) { send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request"); return; }
                        int qidInt;
                        try { qidInt = Integer.parseInt(qid.trim()); }
                        catch (NumberFormatException e) { send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request"); return; }
                        Student s = SVC.get(qidInt);
                        if (s == null) sendNotFound(ex);
                        else send(ex, 200, "application/json", s.toJson());
                        return;
                    }
                    String nameFilter = queryParam(query, "name");
                    String ageFilter = queryParam(query, "age");
                    Integer parsedAgeFilter = null;
                    if (ageFilter != null) {
                        if (ageFilter.isBlank()) { send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request"); return; }
                        try { parsedAgeFilter = Integer.parseInt(ageFilter.trim()); }
                        catch (NumberFormatException e) { send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request"); return; }
                    }
                    if (nameFilter != null || parsedAgeFilter != null) {
                        send(ex, 200, "application/json", filterJson(nameFilter, parsedAgeFilter));
                        return;
                    }
                    send(ex, 200, "application/json", SVC.listJson());
                    return;
                }

                if (method.equals("POST")) {
                    String ct = ex.getRequestHeaders().getFirst("Content-Type");
                    if (ct == null || !ct.toLowerCase().startsWith("application/json")) {
                        send(ex, 415, "text/plain; charset=utf-8", "415 Unsupported Media Type");
                        return;
                    }
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String name = parseJsonString(body, "name");
                    Integer age = parseJsonInt(body, "age");
                    if (name == null || name.isBlank() || age == null || age <= 0) {
                        send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request");
                        return;
                    }
                    Student s = SVC.add(name, age);
                    send(ex, 201, "application/json", s.toJson());
                    return;
                }

                if (method.equals("PUT")) {
                    String qid = queryParam(query, "id");
                    String ct = ex.getRequestHeaders().getFirst("Content-Type");
                    if (ct == null || !ct.toLowerCase().startsWith("application/json")) {
                        send(ex, 415, "text/plain; charset=utf-8", "415 Unsupported Media Type");
                        return;
                    }
                    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Integer id = null;
                    if (qid != null && !qid.isBlank()) {
                        try { id = Integer.parseInt(qid.trim()); } catch (NumberFormatException e) { send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request"); return; }
                    } else {
                        id = parseJsonInt(body, "id");
                    }
                    if (id == null) { send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request: missing id"); return; }
                    String name = parseJsonString(body, "name");
                    Integer age = parseJsonInt(body, "age");
                    if (name == null || name.isBlank() || age == null || age <= 0) {
                        send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request"); return;
                    }
                    if (!SVC.update(id, name, age)) sendNotFound(ex);
                    else send(ex, 200, "application/json", SVC.get(id).toJson());
                    return;
                }

                if (method.equals("DELETE")) {
                    String qid = queryParam(query, "id");
                    Integer id = null;
                    if (qid != null && !qid.isBlank()) {
                        try { id = Integer.parseInt(qid.trim()); } catch (NumberFormatException e) { send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request"); return; }
                    } else {
                        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        id = parseJsonInt(body, "id");
                    }
                    if (id == null) { send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request: missing id"); return; }
                    if (!SVC.delete(id)) sendNotFound(ex);
                    else send(ex, 200, "application/json", "{\"message\":\"Deleted\"}");
                    return;
                }

                ex.getResponseHeaders().set("Allow", "GET, POST, PUT, DELETE");
                send(ex, 405, "text/plain; charset=utf-8", "405 Method Not Allowed");
                return;
            }

            // 5. CRUD /api/students/{id}
            if (path.startsWith("/api/students/")) {
                String sub = path.substring("/api/students/".length());
                while (sub.endsWith("/")) sub = sub.substring(0, sub.length() - 1);
                if (!sub.isEmpty()) {
                    int id;
                    try { id = Integer.parseInt(sub.trim()); }
                    catch (NumberFormatException e) { send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request"); return; }

                    if (method.equals("GET")) {
                        Student s = SVC.get(id);
                        if (s == null) sendNotFound(ex);
                        else send(ex, 200, "application/json", s.toJson());
                        return;
                    }
                    if (method.equals("PUT")) {
                        String ct = ex.getRequestHeaders().getFirst("Content-Type");
                        if (ct == null || !ct.toLowerCase().startsWith("application/json")) {
                            send(ex, 415, "text/plain; charset=utf-8", "415 Unsupported Media Type"); return;
                        }
                        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String name = parseJsonString(body, "name");
                        Integer age = parseJsonInt(body, "age");
                        if (name == null || name.isBlank() || age == null || age <= 0) {
                            send(ex, 400, "text/plain; charset=utf-8", "400 Bad Request"); return;
                        }
                        if (!SVC.update(id, name, age)) sendNotFound(ex);
                        else send(ex, 200, "application/json", SVC.get(id).toJson());
                        return;
                    }
                    if (method.equals("DELETE")) {
                        if (!SVC.delete(id)) sendNotFound(ex);
                        else send(ex, 200, "application/json", "{\"message\":\"Deleted\"}");
                        return;
                    }

                    ex.getResponseHeaders().set("Allow", "GET, PUT, DELETE");
                    send(ex, 405, "text/plain; charset=utf-8", "405 Method Not Allowed");
                    return;
                }
            }

            // Route khong ton tai
            sendNotFound(ex);
        } catch (Exception e) {
            try { send(ex, 500, "text/plain; charset=utf-8", "500 Internal Server Error"); }
            catch (Exception ignored) {}
        }
    }

    // Loc danh sach sinh vien theo ?name= / ?age=
    private String filterJson(String nameFilter, Integer ageFilter) {
        List<Student> all = SVC.list();
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (Student s : all) {
            if (nameFilter != null && (s.name == null || !s.name.toLowerCase().contains(nameFilter.toLowerCase()))) continue;
            if (ageFilter != null && s.age != ageFilter) continue;
            if (count > 0) sb.append(",");
            sb.append(s.toJson());
            count++;
        }
        return sb.append("]").toString();
    }

    public static void send(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] data = (body == null) ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        long len = (data.length == 0) ? -1 : data.length;
        ex.sendResponseHeaders(status, len);
        if (data.length > 0) {
            try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        } else {
            ex.close();
        }
        ServerLogger.log(ex.getRequestMethod(), ex.getRequestURI().getPath(), status, data.length,
            ex.getRemoteAddress() != null ? ex.getRemoteAddress().toString() : "unknown");
    }

    public static String queryParam(String query, String key) {
        if (query == null) return null;
        for (String p : query.split("&")) {
            if (p.equals(key)) return "";
            if (p.startsWith(key + "=")) {
                String v = p.substring(key.length() + 1);
                return urlDecodeOr(v, v);
            }
        }
        return null;
    }

    public static String urlDecodeOr(String s, String fallback) {
        try { return URLDecoder.decode(s, StandardCharsets.UTF_8); }
        catch (IllegalArgumentException e) { return fallback; }
    }

    public static void sendNotFound(HttpExchange ex) throws IOException {
        send(ex, 404, "text/plain; charset=utf-8", "404 Not Found");
    }

    public static int findKeyColon(String json, String key) {
        if (json == null || key == null) return -1;
        int i = 0;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                int start = i + 1;
                int end = start;
                while (end < json.length()) {
                    if (json.charAt(end) == '\\') {
                        end += 2;
                        continue;
                    }
                    if (json.charAt(end) == '"') {
                        break;
                    }
                    end++;
                }
                if (end < json.length()) {
                    String strVal = json.substring(start, end);
                    int after = end + 1;
                    while (after < json.length() && Character.isWhitespace(json.charAt(after))) {
                        after++;
                    }
                    if (after < json.length() && json.charAt(after) == ':') {
                        if (strVal.equals(key)) {
                            return after;
                        }
                        i = after + 1;
                        continue;
                    }
                }
                i = end + 1;
                continue;
            }
            i++;
        }
        return -1;
    }

    public static String parseJsonString(String json, String key) {
        if (json == null) return null;
        int c = findKeyColon(json, key);
        if (c < 0) return null;
        int p = c + 1;
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
        if (p >= json.length() || json.charAt(p) != '"') return null;
        int q1 = p;
        int q2 = q1 + 1;
        while (q2 < json.length()) {
            if (json.charAt(q2) == '"') {
                int backslashes = 0;
                int b = q2 - 1;
                while (b >= 0 && json.charAt(b) == '\\') {
                    backslashes++;
                    b--;
                }
                if (backslashes % 2 == 0) break;
            }
            q2++;
        }
        if (q2 >= json.length()) return null;
        int afterVal = q2 + 1;
        while (afterVal < json.length() && Character.isWhitespace(json.charAt(afterVal))) afterVal++;
        if (afterVal < json.length() && json.charAt(afterVal) != ',' && json.charAt(afterVal) != '}') {
            return null;
        }
        return unescapeJson(json.substring(q1 + 1, q2));
    }

    public static String unescapeJson(String s) {
        if (s == null || s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 < s.length()) {
                            try {
                                int code = Integer.parseInt(s.substring(i + 1, i + 5), 16);
                                sb.append((char) code);
                                i += 4;
                                break;
                            } catch (NumberFormatException ignored) {}
                        }
                        sb.append("\\u");
                        break;
                    default:
                        sb.append('\\').append(next);
                        break;
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static Integer parseJsonInt(String json, String key) {
        if (json == null) return null;
        int c = findKeyColon(json, key);
        if (c < 0) return null;
        int p = c + 1;
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
        if (p >= json.length()) return null;
        boolean quoted = false;
        if (json.charAt(p) == '"') {
            quoted = true;
            p++;
        }
        int s = p;
        if (p < json.length() && (json.charAt(p) == '-' || json.charAt(p) == '+')) {
            p++;
        }
        while (p < json.length() && Character.isDigit(json.charAt(p))) {
            p++;
        }
        if (p == s || (p == s + 1 && (json.charAt(s) == '-' || json.charAt(s) == '+'))) {
            return null;
        }
        int e = p;
        if (quoted) {
            if (e >= json.length() || json.charAt(e) != '"') return null;
            e++;
        }
        int afterVal = e;
        while (afterVal < json.length() && Character.isWhitespace(json.charAt(afterVal))) afterVal++;
        if (afterVal < json.length() && json.charAt(afterVal) != ',' && json.charAt(afterVal) != '}') {
            return null;
        }
        try {
            return Integer.parseInt(json.substring(s, quoted ? e - 1 : e));
        } catch (Exception ex) {
            return null;
        }
    }
}
