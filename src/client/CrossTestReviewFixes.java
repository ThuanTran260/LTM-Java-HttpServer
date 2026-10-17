package client;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

/**
 * Regression runner for file explorer / FTP review fixes.
 * Groups: ftp-delete (task 1), guest-logout (task 2), public-errors (task 6).
 */
public class CrossTestReviewFixes {

    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    /** Asserts public error responses keep the {code,message} shape and carry no filesystem details. */
    static void checkError(HttpResponse<String> resp, int status, String code, String message) {
        check(resp.statusCode() == status, "status expected " + status + " got " + resp.statusCode() + ": " + resp.body());
        String expected = "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
        check(resp.body().equals(expected), "unexpected error body: " + resp.body());
        check(!resp.body().contains("private-canary"), "canary leaked: " + resp.body());
        check(!resp.body().contains("C:"), "drive prefix leaked: " + resp.body());
        check(!resp.body().contains("\\"), "path or escape sequence leaked: " + resp.body());
    }

    public static void main(String[] args) throws Exception {
        String group = args.length == 0 ? "ftp-delete" : args[0];
        switch (group) {
            case "ftp-delete" -> testFtpDeleteTypes();
            case "guest-logout" -> testGuestLogout();
            case "public-errors" -> testPublicErrors();
            default -> throw new IllegalArgumentException("Unknown group: " + group);
        }
        System.out.println("PASS " + group);
    }

    static HttpResponse<String> request(int port, String route, String method,
            String body, String cookie, String csrf) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + route))
                .header("X-File-Explorer", "1").header("Content-Type", "application/json");
        if (cookie != null) b.header("Cookie", cookie);
        if (csrf != null) b.header("X-CSRF-Token", csrf);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static void testGuestLogout() throws Exception {
        try (var fixture = new CrossTestFilesClient.ProcessFixture()) {
            Path ws = fixture.createWorkspace();
            var server = fixture.startHttp(ws, Map.of(
                    "FTP_ADMIN_PASSWORD", "ReviewOnly_2026!",
                    "WEB_FILES_ALLOW_GUEST", "true"));
            int port = server.port();

            var login = request(port, "/api/files/login", "POST",
                    "{\"username\":\"guest\",\"password\":\"\"}", null, null);
            check(login.statusCode() == 200, "guest login");
            String cookie = login.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
            var matcher = java.util.regex.Pattern.compile("\"csrfToken\":\"([^\"]+)\"").matcher(login.body());
            check(matcher.find(), "csrf exists");
            String csrf = matcher.group(1);
            check(request(port, "/api/files/logout", "POST", null, cookie, "wrong").statusCode()
                    == 403, "wrong CSRF rejected");

            check(request(port, "/api/files/logout", "POST", null, cookie, null).statusCode()
                    == 403, "missing CSRF rejected");
            var noHeader = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/files/logout"))
                    .header("Cookie", cookie)
                    .header("X-CSRF-Token", csrf)
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            check(HttpClient.newHttpClient().send(noHeader, HttpResponse.BodyHandlers.ofString()).statusCode()
                    == 403, "missing X-File-Explorer rejected");

            var logout = request(port, "/api/files/logout", "POST", null, cookie, csrf);
            check(logout.statusCode() == 204, "guest logout succeeds");
            check(logout.headers().firstValue("set-cookie").orElse("").contains("Max-Age=0"), "cookie expires");
            check(request(port, "/api/files/session", "GET", null, cookie, null).statusCode()
                    == 401, "old cookie cannot restore session");

            var adminLogin = request(port, "/api/files/login", "POST",
                    "{\"username\":\"admin\",\"password\":\"ReviewOnly_2026!\"}", null, null);
            check(adminLogin.statusCode() == 200, "admin login");
            String adminCookie = adminLogin.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
            var adminMatcher = java.util.regex.Pattern.compile("\"csrfToken\":\"([^\"]+)\"").matcher(adminLogin.body());
            check(adminMatcher.find(), "admin csrf exists");
            check(request(port, "/api/files/logout", "POST", null, adminCookie, adminMatcher.group(1)).statusCode()
                    == 204, "admin logout succeeds");
        }
    }

    static void testPublicErrors() throws Exception {
        try (var fixture = new CrossTestFilesClient.ProcessFixture()) {
            Path root = fixture.createWorkspace().resolve("root");
            var kind = new java.util.concurrent.atomic.AtomicInteger();
            server.SharedFileStore store = new server.SharedFileStore(root, 1024) {
                @Override
                public server.SharedFileStore.DirectoryPage list(String path, int offset, int limit)
                        throws IOException {
                    switch (kind.getAndIncrement()) {
                        case 0 -> throw new server.SharedFileStore.StoreException(
                                "CONFLICT", "locked: C:\\private-canary\\secret.txt");
                        case 1 -> throw new SecurityException("C:\\private-canary");
                        case 2 -> throw new IOException("C:\\private-canary");
                        case 3 -> throw new server.SharedFileStore.StoreException(
                                "CONFLICT", "locked: \\\\server\\share\\private-canary");
                        case 4 -> throw new server.SharedFileStore.StoreException(
                                "CONFLICT", "bad \"name\"\u0007\t\n C:\\private-canary");
                        default -> throw new server.SharedFileStore.StoreException(
                                "WEIRD_CODE", "unsupported: C:\\private-canary");
                    }
                }
            };

            var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            try (var auth = new server.FileSessionAuth("ReviewOnly_2026!", false, java.time.Clock.systemUTC())) {
                http.createContext("/api/files", new server.FilesApiHandler(store, auth));
                http.start();
                int port = http.getAddress().getPort();
                var view = auth.login("admin", "ReviewOnly_2026!");
                String cookie = server.FileSessionAuth.COOKIE_NAME + "=" + view.sessionToken();

                checkError(request(port, "/api/files", "GET", null, cookie, null),
                        409, "CONFLICT", "Operation conflicts with current file state");
                checkError(request(port, "/api/files", "GET", null, cookie, null),
                        403, "FORBIDDEN", "Access denied");
                checkError(request(port, "/api/files", "GET", null, cookie, null),
                        500, "INTERNAL_ERROR", "Unexpected server error");
                checkError(request(port, "/api/files", "GET", null, cookie, null),
                        409, "CONFLICT", "Operation conflicts with current file state");
                checkError(request(port, "/api/files", "GET", null, cookie, null),
                        409, "CONFLICT", "Operation conflicts with current file state");
                checkError(request(port, "/api/files", "GET", null, cookie, null),
                        500, "WEIRD_CODE", "File operation failed");
            } finally {
                http.stop(0);
            }
        }
    }

    static void testFtpDeleteTypes() throws Exception {
        try (var fixture = new CrossTestFilesClient.ProcessFixture()) {
            Path ws = fixture.createWorkspace();
            var server = fixture.startFtp(ws,
                    Map.of("FTP_ADMIN_PASSWORD", "ReviewOnly_2026!"), List.of());
            Path file = server.root().resolve("keep.txt");
            Path dir = server.root().resolve("keep-dir");
            Files.writeString(file, "keep");
            Files.createDirectory(dir);
            try (FtpClient ftp = new FtpClient()) {
                ftp.connect("127.0.0.1", server.port());
                check(ftp.login("admin", "ReviewOnly_2026!"), "admin login");
                check(ftp.sendCommand("RMD keep.txt").code() == 550, "RMD rejects file");
                check(Files.readString(file).equals("keep"), "RMD preserves file bytes");
                check(ftp.sendCommand("DELE keep-dir").code() == 550, "DELE rejects dir");
                check(Files.isDirectory(dir), "DELE preserves directory");
                check(ftp.sendCommand("DELE keep.txt").code() == 250, "DELE accepts file");
                check(ftp.sendCommand("RMD keep-dir").code() == 250, "RMD accepts empty dir");
            }
        }
    }
}
