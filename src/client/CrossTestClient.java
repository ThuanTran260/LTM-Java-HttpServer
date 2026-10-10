package client;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import server.RouterHandler;
import server.SimpleHttpServer;
import mock.MockIisServer;

/**
 * Integration Test Runner tu dong xac thuc toan bo ma tran request:
 * - GET /, GET /api/time, POST /api/message (201, 415, 400)
 * - CRUD /api/students (GET, POST, PUT, DELETE, query filtering ?id=, ?name=, ?age=)
 * - Static files (HTML, CSS, TXT, PNG) va chan Directory Traversal
 * - Route khong ton tai (404 text/plain)
 * - Proxy /iis/ va kiem tra IIS / Mock IIS
 * - Demo Clients (HttpClientGetDemo, HttpClientPostDemo, HttpClientErrorDemo)
 *
 * Co kha nang tu dong khoi dong / goi den Server neu chua chay.
 */
public class CrossTestClient {
    private static int pass = 0;
    private static int fail = 0;
    private static HttpServer autoJavaServer = null;
    private static HttpServer autoMockServer = null;

    public static void main(String[] args) {
        try {
            ensureServersRunning();
            runTests();
        } catch (Throwable t) {
            System.err.println("Fatal exception in test runner: " + t.getMessage());
            t.printStackTrace();
            fail++;
        } finally {
            cleanupServers();
        }

        System.out.println("==================================================");
        System.out.println("==== KET QUA: " + pass + " PASS, " + fail + " FAIL ====");
        System.out.println("==================================================");
        if (fail > 0) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

    private static void ensureServersRunning() throws Exception {
        // 1. Kiem tra Mock IIS (:8081)
        boolean hasMock = probe("http://localhost:8081/");
        if (!hasMock) {
            System.out.println("[TEST RUNNER] Mock IIS chua chay, tu dong khoi dong MockIisServer tren cong 8081...");
            autoMockServer = MockIisServer.startServer(8081);
            Thread.sleep(200);
        }

        // 2. Kiem tra Java Server (:8080)
        boolean hasJava = probe("http://localhost:8080/");
        if (!hasJava) {
            System.out.println("[TEST RUNNER] Java Server chua chay, tu dong khoi dong SimpleHttpServer tren cong 8080...");
            autoJavaServer = SimpleHttpServer.startServer(8080);
            Thread.sleep(200);
        }

        // Reset danh sach sinh vien ve trang thai chuan truoc khi test
        RouterHandler.getStudentService().reset();
    }

    private static void cleanupServers() {
        if (autoJavaServer != null) {
            System.out.println("[TEST RUNNER] Dung SimpleHttpServer tu khoi dong...");
            try { autoJavaServer.stop(0); } catch (Exception ignored) {}
        }
        if (autoMockServer != null) {
            System.out.println("[TEST RUNNER] Dung MockIisServer tu khoi dong...");
            try { autoMockServer.stop(0); } catch (Exception ignored) {}
        }
    }

    private static boolean probe(String url) {
        try {
            HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(600)).build();
            HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(600)).GET().build();
            c.send(r, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void runTests() {
        String base = "http://localhost:8080";
        String iisTarget = probe("http://localhost:80/") ? "http://localhost:80" : "http://localhost:8081";

        System.out.println("\n--- 1. BASIC ROUTES & SLIDE EXAMPLES ---");
        testRequest("1. GET / (Trang chu HTML)", "GET", base + "/", null, null, 200, "text/html", null);
        testRequest("2. GET /api/time (JSON thoi gian)", "GET", base + "/api/time", null, null, 200, "application/json", "\"time\":");
        testRequest("3. POST /api/message (JSON hop le)", "POST", base + "/api/message", "application/json", "{\"text\":\"Xin chao\"}", 201, "application/json", "\"message\":\"Da nhan du lieu\"");
        testRequest("4. POST /api/message (Content-Type sai -> 415)", "POST", base + "/api/message", "text/plain", "Xin chao", 415, "text/plain", "415");
        testRequest("5. POST /api/message (Empty body -> 400)", "POST", base + "/api/message", "application/json", "   ", 400, "text/plain", "400");

        System.out.println("\n--- 2. CRUD SINH VIEN (/api/students) ---");
        testRequest("6. GET /api/students (Danh sach SV ban dau)", "GET", base + "/api/students", null, null, 200, "application/json", "\"name\":\"An\"");
        testRequest("7. GET /api/students?id=1 (Lay SV theo query ?id=1)", "GET", base + "/api/students?id=1", null, null, 200, "application/json", "\"id\":1");
        testRequest("8. GET /api/students?id=999 (SV khong ton tai -> 404)", "GET", base + "/api/students?id=999", null, null, 404, "text/plain", "404");
        testRequest("9. GET /api/students/1 (Lay SV theo path /1)", "GET", base + "/api/students/1", null, null, 200, "application/json", "\"id\":1");
        testRequest("10. GET /api/students?name=An (Loc theo name)", "GET", base + "/api/students?name=An", null, null, 200, "application/json", "\"name\":\"An\"");
        testRequest("11. GET /api/students?age=21 (Loc theo age)", "GET", base + "/api/students?age=21", null, null, 200, "application/json", "\"name\":\"Binh\"");
        testRequest("12. POST /api/students (Them sinh vien moi -> 201)", "POST", base + "/api/students", "application/json", "{\"name\":\"Cuong\",\"age\":22}", 201, "application/json", "\"name\":\"Cuong\"");
        testRequest("13. POST /api/students (Sai Content-Type -> 415)", "POST", base + "/api/students", "text/plain", "name=Cuong", 415, "text/plain", "415");
        testRequest("14. PUT /api/students/1 (Cap nhat SV qua path -> 200)", "PUT", base + "/api/students/1", "application/json", "{\"name\":\"An Updated\",\"age\":25}", 200, "application/json", "\"name\":\"An Updated\"");
        testRequest("15. PUT /api/students?id=1 (Cap nhat SV qua query ?id=1 -> 200)", "PUT", base + "/api/students?id=1", "application/json", "{\"name\":\"An Final\",\"age\":26}", 200, "application/json", "\"name\":\"An Final\"");
        testRequest("16. PUT /api/students/999 (Cap nhat SV khong ton tai -> 404)", "PUT", base + "/api/students/999", "application/json", "{\"name\":\"X\",\"age\":99}", 404, "text/plain", "404");
        testRequest("17. DELETE /api/students/1 (Xoa SV qua path -> 200)", "DELETE", base + "/api/students/1", null, null, 200, "application/json", "\"message\":\"Deleted\"");
        testRequest("18. DELETE /api/students/1 (Xoa lai SV da xoa -> 404)", "DELETE", base + "/api/students/1", null, null, 404, "text/plain", "404");

        System.out.println("\n--- 3. STATIC FILES & DIRECTORY TRAVERSAL ---");
        testRequest("19. GET /index.html (File HTML tinh)", "GET", base + "/index.html", null, null, 200, "text/html", "Welcome");
        testRequest("20. GET /style.css (File CSS)", "GET", base + "/style.css", null, null, 200, "text/css", "background");
        testRequest("21. GET /sample.txt (File van ban TXT)", "GET", base + "/sample.txt", null, null, 200, "text/plain", null);
        testRequest("22. GET /sample.png (File anh binary)", "GET", base + "/sample.png", null, null, 200, "image/png", null);
        testRequest("23. GET /../secret.txt (Chan Directory Traversal -> 404)", "GET", base + "/../secret.txt", null, null, 404, "text/plain", "404");
        testRequest("24. GET /%2e%2e/sample.txt (Chan Traversal URL-encoded -> 404)", "GET", base + "/%2e%2e/sample.txt", null, null, 404, "text/plain", "404");
        testRequest("25. GET /abc (Route khong hop le -> 404 text/plain)", "GET", base + "/abc", null, null, 404, "text/plain", "404 Not Found");

        System.out.println("\n--- 4. PROXY & IIS INTEGRATION ---");
        testRequest("26. GET /iis/ (Proxy sang IIS/Mock IIS -> 200 & Server Header)", "GET", base + "/iis/", null, null, 200, "text/html", null, "Microsoft-IIS");
        testRequest("27. GET / (Truc tiep IIS / Mock IIS " + iisTarget + ")", "GET", iisTarget + "/", null, null, 200, "text/html", null);
        try {
            HttpServer testMock = MockIisServer.startServer(8089);
            testRequest("27b. Mock IIS doc lap (:8089 Server Header)", "GET", "http://localhost:8089/", null, null, 200, "text/html", "IIS Windows Server", "Microsoft-IIS/10.0");
            testMock.stop(0);
        } catch (Exception e) {
            System.err.println("[FAIL] 27b. Mock IIS doc lap: " + e.getMessage());
            fail++;
        }

        System.out.println("\n--- 5. CLIENT DEMO CODE EXECUTION ---");
        testClientDemo("28. HttpClientGetDemo execution", "Status: 200", () -> HttpClientGetDemo.main(new String[]{base + "/api/time"}));
        testClientDemo("29. HttpClientPostDemo execution", "Status: 201", () -> HttpClientPostDemo.main(new String[]{base + "/api/message"}));
        testClientDemo("30. HttpClientErrorDemo execution (Connect error handling)", "Loi ket noi", () -> HttpClientErrorDemo.main(new String[]{"http://localhost:9099/api/time"}));
        testClientDemo("30b. HttpClientErrorDemo execution (2xx Success)", "Thanh cong", () -> HttpClientErrorDemo.main(new String[]{base + "/api/time"}));
        testClientDemo("30c. HttpClientErrorDemo execution (4xx HTTP Error)", "HTTP Error: 404", () -> HttpClientErrorDemo.main(new String[]{base + "/abc"}));

        System.out.println("\n--- 6. ADVERSARIAL EDGE CASES & SLIDE 10 COMPATIBILITY ---");
        testRequest("31. POST /api/students (Backslash escaping)", "POST", base + "/api/students", "application/json", "{\"name\":\"Nguyen\\\\\",\"age\":20}", 201, "application/json", "\"name\":\"Nguyen\\\\\"");
        testRequest("32. POST /api/students (Unicode escape \\uXXXX)", "POST", base + "/api/students", "application/json", "{\"name\":\"\\u0110\\u1ed7 \\u00c1nh\",\"age\":22}", 201, "application/json", "\"name\":\"Đỗ Ánh\"");
        testRequest("33. GET /api/students?name=Nguyen (Tim kiem SV ten Nguyen)", "GET", base + "/api/students?name=Nguyen", null, null, 200, "application/json", "\"name\":\"Nguyen");
        testRequest("34. GET /students (Slide 10 Route shorthand)", "GET", base + "/students", null, null, 200, "application/json", "\"name\":");
        testRequest("35. GET /students/2 (Slide 10 Route shorthand ?id / {id})", "GET", base + "/students/2", null, null, 200, "application/json", "\"id\":2");
        testRequest("36. POST /api/time (Method Not Allowed -> 405 Allow: GET)", "POST", base + "/api/time", null, null, 405, "text/plain", "405", "Allow", "GET");
        testRequest("37. POST /api/students/1 (Method Not Allowed on ID -> 405 Allow: GET, PUT, DELETE)", "POST", base + "/api/students/1", "application/json", "{}", 405, "text/plain", "405", "Allow", "GET");
        testRequest("38. POST /api/students (Empty name -> 400)", "POST", base + "/api/students", "application/json", "{\"name\":\"\",\"age\":20}", 400, "text/plain", "400");
        testRequest("39. POST /api/students (Negative age -> 400)", "POST", base + "/api/students", "application/json", "{\"name\":\"Test\",\"age\":-5}", 400, "text/plain", "400");
        testRequest("40. DELETE /api/students (Xoa SV qua JSON body -> 200)", "DELETE", base + "/api/students", "application/json", "{\"id\":2}", 200, "application/json", "\"message\":\"Deleted\"");
        testRequest("41. GET /api/students/3/ (Trailing slash on student ID -> 200)", "GET", base + "/api/students/3/", null, null, 200, "application/json", "\"id\":3");
        testRequest("42. GET /students/3/ (Slide 10 shorthand with trailing slash -> 200)", "GET", base + "/students/3/", null, null, 200, "application/json", "\"id\":3");
        testRequest("43. POST /api/students (Key string embedded inside earlier value)", "POST", base + "/api/students", "application/json", "{\"bio\":\"Likes \\\"name\\\": oops\", \"name\":\"Hoang\", \"age\":23}", 201, "application/json", "\"name\":\"Hoang\"");
        testRequest("44. GET /iis/?search=hello%20world (Proxy with percent-encoded query -> 200)", "GET", base + "/iis/?search=hello%20world", null, null, 200, "text/html", null);
        testRequest("45. GET /test%00.txt (Malformed static path -> 404 text/plain)", "GET", base + "/test%00.txt", null, null, 404, "text/plain", "404 Not Found");
        testRequest("46. GET /time (Shorthand alias -> 200)", "GET", base + "/time", null, null, 200, "application/json", "\"time\":");
        testRequest("47. POST /message (Shorthand alias -> 201)", "POST", base + "/message", "application/json", "{\"text\":\"Hello\"}", 201, "application/json", "\"message\":\"Da nhan du lieu\"");
        testRequest("48. POST /api/students (Non-string name type integer -> 400)", "POST", base + "/api/students", "application/json", "{\"name\": 123, \"age\": 20}", 400, "text/plain", "400");
        testRequest("49. POST /api/students (Non-string name type boolean -> 400)", "POST", base + "/api/students", "application/json", "{\"name\": true, \"age\": 20}", 400, "text/plain", "400");
        testRequest("50. POST /api/students (Invalid integer with suffix -> 400)", "POST", base + "/api/students", "application/json", "{\"name\": \"David\", \"age\": \"20years\"}", 400, "text/plain", "400");
        testRequest("51. POST /api/students (Non-integer age float -> 400)", "POST", base + "/api/students", "application/json", "{\"name\": \"David\", \"age\": 20.5}", 400, "text/plain", "400");
        testRequest("52. GET /api/students?age=abc (Invalid age query -> 400)", "GET", base + "/api/students?age=abc", null, null, 400, "text/plain", "400");
        testRequest("53. PUT /api/students/1 (Non-string name type -> 400)", "PUT", base + "/api/students/1", "application/json", "{\"name\": 999, \"age\": 20}", 400, "text/plain", "400");

        System.out.println("\n--- 7. CONCURRENCY & STREAMING STRESS ---");
        testConcurrency("54. Concurrency stress test (50 concurrent requests)", base + "/api/time", 50);
        testLargeStaticStreaming("55. Large static file streaming (>250KB without truncation)", base);
        testEmptyStaticFile("56. Empty static file (0 bytes without chunked transfer)", base);
    }

    private static void testRequest(String testName, String method, String url, String contentType, String reqBody,
                                    int expectedStatus, String expectedContentTypeSub, String expectedBodySub) {
        testRequest(testName, method, url, contentType, reqBody, expectedStatus, expectedContentTypeSub, expectedBodySub, null, null);
    }

    private static void testRequest(String testName, String method, String url, String contentType, String reqBody,
                                    int expectedStatus, String expectedContentTypeSub, String expectedBodySub, String expectedServerHeader) {
        testRequest(testName, method, url, contentType, reqBody, expectedStatus, expectedContentTypeSub, expectedBodySub, "Server", expectedServerHeader);
    }

    private static void testRequest(String testName, String method, String url, String contentType, String reqBody,
                                    int expectedStatus, String expectedContentTypeSub, String expectedBodySub,
                                    String expectedHeaderName, String expectedHeaderSub) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3));

            if (contentType != null) {
                builder.header("Content-Type", contentType);
            }

            if (method.equals("GET")) {
                builder.GET();
            } else if (method.equals("DELETE")) {
                if (reqBody != null && !reqBody.isEmpty()) {
                    builder.method("DELETE", HttpRequest.BodyPublishers.ofString(reqBody));
                } else {
                    builder.DELETE();
                }
            } else if (method.equals("POST")) {
                builder.POST(HttpRequest.BodyPublishers.ofString(reqBody == null ? "" : reqBody));
            } else if (method.equals("PUT")) {
                builder.PUT(HttpRequest.BodyPublishers.ofString(reqBody == null ? "" : reqBody));
            }

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String resCt = response.headers().firstValue("Content-Type").orElse("");
            String body = response.body() == null ? "" : response.body();
            String hVal = expectedHeaderName != null ? response.headers().firstValue(expectedHeaderName).orElse("") : "";

            boolean statusOk = (status == expectedStatus);
            boolean ctOk = (expectedContentTypeSub == null || resCt.toLowerCase().contains(expectedContentTypeSub.toLowerCase()));
            boolean bodyOk = (expectedBodySub == null || body.contains(expectedBodySub));
            boolean headerOk = (expectedHeaderName == null || hVal.contains(expectedHeaderSub));

            if (statusOk && ctOk && bodyOk && headerOk) {
                String extra = expectedHeaderName != null ? " [" + expectedHeaderName + ": " + hVal + "]" : "";
                System.out.println("[PASS] " + testName + " -> Status " + status + " (" + resCt + ")" + extra);
                pass++;
            } else {
                System.err.println("[FAIL] " + testName + ":");
                if (!statusOk) System.err.println("       Status mong doi: " + expectedStatus + ", thuc te: " + status);
                if (!ctOk) System.err.println("       Content-Type mong doi chua: " + expectedContentTypeSub + ", thuc te: " + resCt);
                if (!bodyOk) System.err.println("       Body mong doi chua: '" + expectedBodySub + "', thuc te: '" + body + "'");
                if (!headerOk) System.err.println("       " + expectedHeaderName + " Header mong doi chua: '" + expectedHeaderSub + "', thuc te: '" + hVal + "'");
                fail++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] " + testName + " -> Exception: " + e.getMessage());
            fail++;
        }
    }

    private static void testClientDemo(String testName, String expectedOutputSub, ThrowingRunnable action) {
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            PrintStream ps = new PrintStream(buffer, true);
            System.setOut(ps);
            System.setErr(ps);
            action.run();
            String captured = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
            if (expectedOutputSub != null && !captured.contains(expectedOutputSub)) {
                origErr.println("[FAIL] " + testName + ": Output khong chua '" + expectedOutputSub + "':\n" + captured.trim());
                fail++;
            } else {
                origOut.println("[PASS] " + testName + " -> OK");
                pass++;
            }
        } catch (Throwable t) {
            origErr.println("[FAIL] " + testName + " -> " + t.getMessage());
            fail++;
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }
    }

    private static void testConcurrency(String testName, String url, int threadCount) {
        try {
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
            java.util.concurrent.atomic.AtomicInteger successes = new java.util.concurrent.atomic.AtomicInteger(0);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
                        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                        if (res.statusCode() == 200) {
                            successes.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        latch.countDown();
                    }
                });
            }
            boolean finished = latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
            executor.shutdown();

            if (finished && successes.get() == threadCount) {
                System.out.println("[PASS] " + testName + " (" + successes.get() + "/" + threadCount + " concurrent requests OK)");
                pass++;
            } else {
                System.err.println("[FAIL] " + testName + ": thanh cong " + successes.get() + "/" + threadCount);
                fail++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] " + testName + " -> Exception: " + e.getMessage());
            fail++;
        }
    }

    private static void testLargeStaticStreaming(String testName, String base) {
        java.nio.file.Path largePath = java.nio.file.Paths.get("wwwroot", "large_test.txt");
        try {
            byte[] chunk = "A".repeat(1024).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(largePath)) {
                for (int i = 0; i < 256; i++) {
                    os.write(chunk);
                }
            }
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/large_test.txt")).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<byte[]> response = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            int len = response.body() != null ? response.body().length : 0;
            if (status == 200 && len == 256 * 1024) {
                System.out.println("[PASS] " + testName + " -> Status 200 (" + len + " bytes streamed without truncation)");
                pass++;
            } else {
                System.err.println("[FAIL] " + testName + ": status=" + status + ", length=" + len + " (mong doi 262144 bytes)");
                fail++;
            }
        } catch (Exception e) {
            System.err.println("[FAIL] " + testName + " -> " + e.getMessage());
            fail++;
        } finally {
            try { java.nio.file.Files.deleteIfExists(largePath); } catch (Exception ignored) {}
        }
    }

    private static void testEmptyStaticFile(String testName, String base) {
        java.nio.file.Path emptyPath = java.nio.file.Paths.get("wwwroot", "empty_test.txt");
        try {
            java.nio.file.Files.write(emptyPath, new byte[0]);
            testRequest(testName, "GET", base + "/empty_test.txt", null, null, 200, "text/plain", null);
        } catch (Exception e) {
            System.err.println("[FAIL] " + testName + " -> " + e.getMessage());
            fail++;
        } finally {
            try { java.nio.file.Files.deleteIfExists(emptyPath); } catch (Exception ignored) {}
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
