package server;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

public class SimpleHttpServer {
    public static HttpServer startServer(int port) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        SiteManager sites = new SiteManager(Paths.get("sites"), Paths.get("sites.json"), port);
        sites.load();
        server.createContext("/api/sites", new SitesApiHandler(sites));
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
