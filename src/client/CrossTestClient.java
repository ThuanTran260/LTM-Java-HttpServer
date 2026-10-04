package client;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
// Ma tran moc cheo 2x2 + proxy. Tu dong fallback 80 -> 8081.
public class CrossTestClient {
    static int pass = 0, fail = 0;
    public static void main(String[] args) throws Exception {
        String base8080 = "http://localhost:8080";
        String iisDirect = detectIis();
        test("1. Java -> Java Server /api/time", base8080 + "/api/time", 200);
        test("2. Java -> Java Server /iis/ (proxy)", base8080 + "/iis/", 200);
        test("3. Java -> IIS truc tiep /", iisDirect + "/", 200);
        testPost("4. POST JSON /api/message", base8080 + "/api/message", 201);
        test("5. Java -> Java Server / (static)", base8080 + "/", 200);
        testExpect("6. 404 /abc", base8080 + "/abc", 404);
        System.out.println("==== KET QUA: " + pass + " PASS, " + fail + " FAIL ====");
        if (fail > 0) System.exit(1);
    }
    static String detectIis() {
        if (probe("http://localhost:80/")) return "http://localhost:80";
        return "http://localhost:8081";
    }
    static boolean probe(String url) {
        try {
            HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(1)).GET().build();
            return c.send(r, HttpResponse.BodyHandlers.discarding()).statusCode() < 500;
        } catch (Exception e) { return false; }
    }
    static void test(String name, String url, int expect) {
        try {
            HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpResponse<String> r = c.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            boolean ok = r.statusCode() == expect;
            System.out.println((ok ? "[PASS] " : "[FAIL] ") + name + " -> " + r.statusCode() + " CT=" + r.headers().firstValue("Content-Type").orElse(""));
            if (ok) pass++; else fail++;
        } catch (Exception e) { System.out.println("[FAIL] " + name + " EX: " + e.getMessage()); fail++; }
    }
    static void testExpect(String name, String url, int expect) { test(name, url, expect); }
    static void testPost(String name, String url, int expect) {
        try {
            HttpClient c = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"Xin chao\"}")).build();
            HttpResponse<String> r = c.send(req, HttpResponse.BodyHandlers.ofString());
            boolean ok = r.statusCode() == expect;
            System.out.println((ok ? "[PASS] " : "[FAIL] ") + name + " -> " + r.statusCode() + " " + r.body());
            if (ok) pass++; else fail++;
        } catch (Exception e) { System.out.println("[FAIL] " + name + " EX: " + e.getMessage()); fail++; }
    }
}
