package server;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

public class SimpleHttpServer {
    public static HttpServer startServer(int port) throws Exception {
        return startServer(port, null, null);
    }

    public static HttpServer startServer(int port, SharedFileStore customStore, FileSessionAuth customAuth) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        SiteManager sites = new SiteManager(Paths.get("sites"), Paths.get("sites.json"), port);
        sites.load();
        server.createContext("/api/sites", new SitesApiHandler(sites));

        SharedFileStore store = customStore;
        FileSessionAuth auth = customAuth;
        if (store == null || auth == null) {
            try {
                FileStoreConfig config = FileStoreConfig.fromEnvironment();
                if (store == null) {
                    try {
                        store = new SharedFileStore(config.root(), config.maxFileBytes());
                    } catch (Exception e) {
                        System.err.println("[WARN] Failed to initialize SharedFileStore: " + e.getMessage());
                    }
                }
                if (auth == null) {
                    auth = FileSessionAuth.fromEnvironment(config.allowWebGuest());
                }
            } catch (Exception e) {
                System.err.println("[WARN] Failed to load FileStoreConfig: " + e.getMessage());
            }
        }
        if (auth == null) {
            auth = new FileSessionAuth(null, false, null);
        }
        server.createContext("/api/files", new FilesApiHandler(store, auth));

        server.createContext("/iis", new ProxyHandler());
        server.createContext("/fetch", new WebFetchHandler());
        server.createContext("/", new RouterHandler());
        // Virtual threads cho moi request dong thoi (Java standard)
        try {
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            System.out.println("Executor: virtual threads");
        } catch (Exception e) {
            server.setExecutor(Executors.newFixedThreadPool(10));
            System.out.println("Executor: fixed pool (fallback)");
        }
        server.start();
        System.out.println("HTTP Server started.");
        System.out.println("Listening on http://localhost:" + port + "/");
        System.out.println("Proxy IIS: http://localhost:" + port + "/iis/  -> IIS that :80");
        System.out.println("Web Fetcher: http://localhost:" + port + "/fetch?url=...  (format=json)");
        System.out.println("Quan ly nhieu web: http://localhost:" + port + "/admin.html  (API: /api/sites)");
        System.out.println("Quan ly file: http://localhost:" + port + "/files.html  (API: /api/files)");
        return server;
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (Exception ignored) {}
        }
        startServer(port);
    }
}
