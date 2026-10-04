package mock;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
// Gia lap IIS that khi may khong co quyen Admin / chua bat IIS.
// Tra header Server: Microsoft-IIS/10.0 de Client kiem chung duoc.
public class MockIisServer {
    public static void main(String[] args) throws Exception {
        int port = 8081;
        if (args.length > 0) { try { port = Integer.parseInt(args[0]); } catch (Exception ignored) {} }
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        final int mockPort = port;
        s.createContext("/", ex -> {
            String html = "<!doctype html><meta charset='utf-8'><h1>IIS Windows Server</h1><p>Mock IIS 10.0 - port " + mockPort + "</p>";
            byte[] d = html.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.getResponseHeaders().set("Server", "Microsoft-IIS/10.0");
            ex.sendResponseHeaders(200, d.length);
            try (var os = ex.getResponseBody()) { os.write(d); }
            System.out.println("[MOCK IIS] " + ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " -> 200");
        });
        try { s.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); }
        catch (Exception e) { s.setExecutor(Executors.newFixedThreadPool(4)); }
        s.start();
        System.out.println("Mock IIS listening on http://localhost:" + port + "/  (Server: Microsoft-IIS/10.0)");
    }
}
