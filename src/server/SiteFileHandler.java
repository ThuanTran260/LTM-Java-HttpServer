package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Phuc vu file tinh cua mot site (mot thu muc). Dung lai StaticFileHandler de giu nguyen chan Directory Traversal
 * va quy tac Content-Type; chi nhan GET, route khong co file -> 404 text/plain.
 */
final class SiteFileHandler implements HttpHandler {
    private final Path root;

    SiteFileHandler(Path root) {
        this.root = root;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod())) {
                ex.getResponseHeaders().set("Allow", "GET");
                RouterHandler.send(ex, 405, "text/plain; charset=utf-8", "405 Method Not Allowed");
                return;
            }
            String path = ex.getRequestURI().getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (!StaticFileHandler.tryServe(ex, path, root)) {
                RouterHandler.sendNotFound(ex);
            }
        } catch (Exception e) {
            try {
                RouterHandler.send(ex, 500, "text/plain; charset=utf-8", "500 Internal Server Error");
            } catch (Exception ignored) {
                // phan hoi da gui mot phan: bo qua
            }
        }
    }
}
