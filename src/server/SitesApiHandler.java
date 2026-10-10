package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * REST quan ly cac web (site), chi cho phep goi tu may local (loopback):
 *   GET    /api/sites                danh sach
 *   POST   /api/sites                them {"name":"blog","port":9001} -> 201
 *   GET    /api/sites/{name}         chi tiet
 *   POST   /api/sites/{name}/start   bat -> 200
 *   POST   /api/sites/{name}/stop    tat -> 200
 *   DELETE /api/sites/{name}         xoa khoi danh sach (giu nguyen thu muc) -> 200
 *   GET    /api/sites/{name}/files   cay file web cua site (chi doc)
 * Moi POST bat buoc Content-Type: application/json (form POST tu trang khac se bi 415).
 */
public class SitesApiHandler implements HttpHandler {
    private static final String BASE = "/api/sites";
    private static final String JSON = "application/json";
    private static final String TEXT = "text/plain; charset=utf-8";

    private final SiteManager manager;

    public SitesApiHandler(SiteManager manager) {
        this.manager = manager;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (!isLoopback(ex.getRemoteAddress())) {
                RouterHandler.send(ex, 403, TEXT, "403 Forbidden: chi cho phep goi tu may local");
                return;
            }
            String path = ex.getRequestURI().getPath();
            String sub = path.length() > BASE.length() ? path.substring(BASE.length()) : "";
            while (sub.endsWith("/")) {
                sub = sub.substring(0, sub.length() - 1);
            }
            String method = ex.getRequestMethod();

            if (sub.isEmpty()) {
                if (method.equals("GET")) {
                    RouterHandler.send(ex, 200, JSON, manager.listJson());
                } else if (method.equals("POST")) {
                    addSite(ex);
                } else {
                    methodNotAllowed(ex, "GET, POST");
                }
                return;
            }
            if (!sub.startsWith("/")) {
                RouterHandler.sendNotFound(ex);
                return;
            }

            String[] parts = sub.substring(1).split("/", -1);
            String name = RouterHandler.urlDecodeOr(parts[0], parts[0]);
            if (parts.length == 1) {
                if (method.equals("GET")) {
                    RouterHandler.send(ex, 200, JSON, manager.get(name).toJson());
                } else if (method.equals("DELETE")) {
                    manager.remove(name);
                    RouterHandler.send(ex, 200, JSON, "{\"message\":\"Deleted\"}");
                } else {
                    methodNotAllowed(ex, "GET, DELETE");
                }
                return;
            }
            if (parts.length == 2 && parts[1].equals("files")) {
                if (!method.equals("GET")) {
                    methodNotAllowed(ex, "GET");
                    return;
                }
                RouterHandler.send(ex, 200, JSON, manager.filesJson(name));
                return;
            }
            if (parts.length == 2 && (parts[1].equals("start") || parts[1].equals("stop"))) {
                if (!method.equals("POST")) {
                    methodNotAllowed(ex, "POST");
                    return;
                }
                if (!requireJson(ex)) {
                    return;
                }
                SiteManager.SiteView v = parts[1].equals("start") ? manager.start(name) : manager.stop(name);
                RouterHandler.send(ex, 200, JSON, v.toJson());
                return;
            }
            RouterHandler.sendNotFound(ex);
        } catch (SiteManager.SiteException e) {
            RouterHandler.send(ex, e.status(), TEXT, e.status() + " " + reason(e.status()) + ": " + e.getMessage());
        } catch (Exception e) {
            try {
                RouterHandler.send(ex, 500, TEXT, "500 Internal Server Error");
            } catch (Exception ignored) {
                // phan hoi da gui mot phan: bo qua
            }
        }
    }

    private void addSite(HttpExchange ex) throws IOException, SiteManager.SiteException {
        if (!requireJson(ex)) {
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String name = RouterHandler.parseJsonString(body, "name");
        Integer port = RouterHandler.parseJsonInt(body, "port");
        if (name == null || port == null) {
            RouterHandler.send(ex, 400, TEXT, "400 Bad Request: can JSON {\"name\":\"ten-site\",\"port\":9001}");
            return;
        }
        RouterHandler.send(ex, 201, JSON, manager.add(name, port).toJson());
    }

    private static boolean requireJson(HttpExchange ex) throws IOException {
        String ct = ex.getRequestHeaders().getFirst("Content-Type");
        if (ct == null || !ct.toLowerCase().startsWith(JSON)) {
            RouterHandler.send(ex, 415, TEXT, "415 Unsupported Media Type");
            return false;
        }
        return true;
    }

    private static void methodNotAllowed(HttpExchange ex, String allow) throws IOException {
        ex.getResponseHeaders().set("Allow", allow);
        RouterHandler.send(ex, 405, TEXT, "405 Method Not Allowed");
    }

    /** Chi dia chi loopback (127.0.0.1 hoac ::1) moi duoc goi API quan ly. */
    public static boolean isLoopback(InetSocketAddress remote) {
        return remote != null && remote.getAddress() != null && remote.getAddress().isLoopbackAddress();
    }

    private static String reason(int status) {
        switch (status) {
            case 400: return "Bad Request";
            case 404: return "Not Found";
            case 409: return "Conflict";
            default: return "Internal Server Error";
        }
    }
}
