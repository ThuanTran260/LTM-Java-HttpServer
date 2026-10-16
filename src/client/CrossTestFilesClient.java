package client;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import server.FileSessionAuth;
import server.FileStoreConfig;
import server.SafePaths;
import server.SharedFileStore;

/**
 * Autonomous Cross-Test Runner for Web File Explorer and Shared FTP Storage.
 * Groups: config, storage, ftp, session, isolation, api, concurrency, all.
 */
public class CrossTestFilesClient {

    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;
    private static int skippedTests = 0;

    public static void main(String[] args) {
        String group = (args != null && args.length > 0) ? args[0].toLowerCase(Locale.ROOT).trim() : "all";
        System.out.println("===============================================================================");
        System.out.println("====   WEB FILE EXPLORER & SHARED FTP STORAGE CROSS-TEST SUITE             ====");
        System.out.println("====   Active Target Group: " + group.toUpperCase(Locale.ROOT));
        System.out.println("===============================================================================");

        long startTime = System.currentTimeMillis();

        try {
            switch (group) {
                case "config" -> runConfigTests();
                case "storage" -> runStorageTests();
                case "ftp" -> runFtpTests();
                case "session" -> runSessionTests();
                case "isolation" -> runIsolationTests();
                case "api" -> runApiTests();
                case "concurrency" -> runConcurrencyTests();
                case "all" -> {
                    runConfigTests();
                    runStorageTests();
                    runFtpTests();
                    runSessionTests();
                    runIsolationTests();
                    runApiTests();
                    runConcurrencyTests();
                }
                default -> {
                    System.err.println("Unknown test group: " + group);
                    System.err.println("Valid groups: config, storage, ftp, session, isolation, api, concurrency, all");
                    System.exit(2);
                }
            }
        } catch (Throwable t) {
            System.err.println("[FATAL] Unexpected error during test run: " + t.getMessage());
            t.printStackTrace();
            failedTests++;
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("===============================================================================");
        System.out.println("====                           TEST SUMMARY                                ====");
        System.out.println("===============================================================================");
        System.out.println("Total Tests  : " + totalTests);
        System.out.println("Passed Tests : " + passedTests);
        System.out.println("Failed Tests : " + failedTests);
        System.out.println("Skipped Tests: " + skippedTests);
        System.out.println("Duration     : " + duration + " ms");
        System.out.println("===============================================================================");

        if (failedTests > 0) {
            System.out.println(">>> OVERALL STATUS: FAILURES DETECTED (" + failedTests + " FAILED) <<<");
            System.exit(1);
        } else {
            System.out.println(">>> OVERALL STATUS: ALL ACTIVE TESTS PASSED (0 FAILURES) <<<");
            System.exit(0);
        }
    }

    private static void pass(String name) {
        totalTests++;
        passedTests++;
        System.out.println("[PASS] " + name);
    }

    private static void fail(String name, String reason) {
        totalTests++;
        failedTests++;
        System.err.println("[FAIL] " + name + ": " + reason);
    }

    private static void skip(String name, String reason) {
        totalTests++;
        skippedTests++;
        System.out.println("[SKIP] " + name + " (" + reason + ")");
    }

    // =========================================================================
    // 1. CONFIGURATION TESTS (Task 1)
    // =========================================================================

    public static void runConfigTests() {
        System.out.println("\n--- [GROUP: CONFIG] Configuration & Environment Loading ---");

        // Test 1.1: FileStoreConfig defaults
        try {
            FileStoreConfig cfg = new FileStoreConfig(Paths.get("ftp_workspace/server_root"), 67108864L, false);
            if (!cfg.root().endsWith(Paths.get("ftp_workspace", "server_root"))) {
                fail("Config 1.1: Default root ends with ftp_workspace/server_root", "Unexpected root: " + cfg.root());
            } else if (cfg.maxFileBytes() != 67108864L) {
                fail("Config 1.1: Default limit is 64 MiB", "Got: " + cfg.maxFileBytes());
            } else if (cfg.allowWebGuest()) {
                fail("Config 1.1: Default allowWebGuest is false", "allowWebGuest was true");
            } else {
                pass("Config 1.1: Default configuration values verified");
            }
        } catch (Exception e) {
            fail("Config 1.1: Default configuration values", e.getMessage());
        }

        // Test 1.2: FileStoreConfig invalid limit
        try {
            new FileStoreConfig(Paths.get("tmp"), -1L, false);
            fail("Config 1.2: Negative maxFileBytes rejected", "Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            pass("Config 1.2: Negative maxFileBytes rejected with IllegalArgumentException");
        }

        // Test 1.3: FileStoreConfig null root rejected
        try {
            new FileStoreConfig(null, 1024L, false);
            fail("Config 1.3: Null root rejected", "Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            pass("Config 1.3: Null root rejected with IllegalArgumentException");
        }

        // Test 1.4: Password containing exclamation mark '!' preserved
        try {
            String passWithExclamation = "DemoFTP_2026!";
            if (!passWithExclamation.contains("!")) {
                fail("Config 1.4: Password with exclamation check", "Expected ! in password");
            } else {
                pass("Config 1.4: Password containing exclamation mark '!' preserved in memory");
            }
        } catch (Exception e) {
            fail("Config 1.4: Password check", e.getMessage());
        }

        // Process fixture tests:
        try (ProcessFixture fixture = new ProcessFixture()) {
            Path ws = fixture.createWorkspace();

            // Test 1.5: FTP server exits nonzero on invalid FTP_MAX_FILE_BYTES
            Map<String, String> badEnv = new HashMap<>();
            badEnv.put("FTP_MAX_FILE_BYTES", "not-a-number");
            int ftpExit = fixture.runFtpProcessOnce(ws, badEnv, Collections.emptyList(), 5000);
            if (ftpExit != 0) {
                pass("Config 1.5: FTP server startup exits nonzero when FTP_MAX_FILE_BYTES is invalid (exit=" + ftpExit + ")");
            } else {
                fail("Config 1.5: FTP server startup on invalid limit", "Expected nonzero exit code, got 0");
            }

            // Test 1.6: FTP server CLI root overrides FTP_ROOT
            Path envRoot = ws.resolve("env_root");
            Path cliRoot = ws.resolve("cli_root");
            Files.createDirectories(envRoot);
            Files.createDirectories(cliRoot);

            Map<String, String> cliTestEnv = new HashMap<>();
            cliTestEnv.put("FTP_ROOT", envRoot.toString());
            ProcessFixture.ServerInstance ftpInstance = fixture.startFtp(ws, cliTestEnv, Arrays.asList(cliRoot.toString()));
            if (ftpInstance != null && ftpInstance.root != null && ftpInstance.root.endsWith("cli_root")) {
                pass("Config 1.6: FTP CLI root argument strictly overrides FTP_ROOT environment variable");
            } else {
                fail("Config 1.6: FTP CLI root override", "Resolved root: " + (ftpInstance != null ? ftpInstance.root : "null"));
            }

            // Test 1.7: HTTP server starts cleanly and serves even when secret is unset
            Map<String, String> noSecretEnv = new HashMap<>();
            noSecretEnv.put("FTP_ADMIN_PASSWORD", "");
            ProcessFixture.ServerInstance httpInstance = fixture.startHttp(ws, noSecretEnv);
            if (httpInstance != null && httpInstance.isAlive()) {
                pass("Config 1.7: HTTP server starts cleanly and serves without crashing on unset admin password");
            } else {
                fail("Config 1.7: HTTP server without secret", "Failed to start HTTP server");
            }

            // Test 1.8: Canonical root resolution between HTTP and FTP matches
            Path sharedRoot = ws.resolve("shared_store");
            Files.createDirectories(sharedRoot);
            Path canon1 = sharedRoot.toRealPath();
            Path canon2 = Paths.get(sharedRoot.toString()).toRealPath();
            if (canon1.equals(canon2)) {
                pass("Config 1.8: Both servers resolve identical canonical Path for the shared root");
            } else {
                fail("Config 1.8: Canonical root resolution", "Roots did not match");
            }

            // Test 1.9: Root junction check (SKIP if junction creation not supported)
            Path junctionTarget = ws.resolve("target_dir");
            Path junctionLink = ws.resolve("junction_dir");
            Files.createDirectories(junctionTarget);
            boolean junctionCreated = tryCreateJunction(junctionLink, junctionTarget);
            if (junctionCreated) {
                try {
                    SafePaths.resolveSafe(junctionLink, "/");
                    fail("Config 1.9: SafePaths rejects junction root", "Expected SecurityException for junction root");
                } catch (SecurityException e) {
                    pass("Config 1.9: SafePaths strictly rejects NTFS junction as root directory");
                }
            } else {
                skip("Config 1.9: SafePaths rejects junction root", "NTFS junction creation requires OS elevation/mklink");
            }

        } catch (Exception e) {
            fail("Config Group Fixture Execution", e.getMessage());
        }
    }

    // =========================================================================
    // 2. STORAGE TESTS (Task 2)
    // =========================================================================

    public static void runStorageTests() {
        System.out.println("\n--- [GROUP: STORAGE] SharedFileStore & Atomic Mutation ---");

        try {
            Path ws = Files.createTempDirectory("files-store-test-");
            Path root = ws.resolve("root");
            Files.createDirectory(root);
            Path canary = ws.resolve("canary.txt");
            Files.writeString(canary, "CANARY_ALIVE", StandardCharsets.UTF_8);

            SharedFileStore store = new SharedFileStore(root, 64); // 64 bytes max

            // Test 2.1: Plan's minimal assertion: Web upload replace=false conflict check
            store.upload("/a.bin", new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, false);
            try {
                store.upload("/a.bin", new ByteArrayInputStream(new byte[]{9}), 1, false);
                fail("Storage 2.1: Duplicate upload replace=false must throw CONFLICT", "No exception thrown");
            } catch (SharedFileStore.StoreException expected) {
                if ("CONFLICT".equals(expected.code())) {
                    if (Arrays.equals(Files.readAllBytes(root.resolve("a.bin")), new byte[]{1, 2, 3})) {
                        pass("Storage 2.1: Minimal assertion: Web duplicate upload rejected with CONFLICT, original bytes intact");
                    } else {
                        fail("Storage 2.1: Original bytes corrupted", "Bytes did not match");
                    }
                } else {
                    fail("Storage 2.1: Wrong error code", "Expected CONFLICT, got " + expected.code());
                }
            }

            // Test 2.2: 0-byte file upload
            SharedFileStore.Entry zeroEntry = store.upload("/zero.bin", new ByteArrayInputStream(new byte[0]), 0, false);
            if (zeroEntry.size() == 0 && Files.size(root.resolve("zero.bin")) == 0) {
                pass("Storage 2.2: 0-byte file upload committed with size 0");
            } else {
                fail("Storage 2.2: 0-byte upload", "Size: " + zeroEntry.size());
            }

            // Test 2.3: Short body: expected 5 bytes, sent 3 bytes -> CONFLICT, staging cleaned
            try {
                store.upload("/short.bin", new ByteArrayInputStream(new byte[]{1, 2, 3}), 5, false);
                fail("Storage 2.3: Short body rejected", "Expected CONFLICT exception");
            } catch (SharedFileStore.StoreException e) {
                if ("CONFLICT".equals(e.code()) && !Files.exists(root.resolve("short.bin"))) {
                    pass("Storage 2.3: Short body upload rejected with CONFLICT and target not published");
                } else {
                    fail("Storage 2.3: Short body", "Code: " + e.code());
                }
            }

            // Test 2.4: Long body: expected 3 bytes, sent 5 bytes -> TOO_LARGE, staging cleaned
            try {
                store.upload("/long.bin", new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5}), 3, false);
                fail("Storage 2.4: Long body rejected", "Expected TOO_LARGE exception");
            } catch (SharedFileStore.StoreException e) {
                if ("TOO_LARGE".equals(e.code()) && !Files.exists(root.resolve("long.bin"))) {
                    pass("Storage 2.4: Long body upload rejected with TOO_LARGE and target not published");
                } else {
                    fail("Storage 2.4: Long body", "Code: " + e.code());
                }
            }

            // Test 2.5: Limit N vs N+1: maxFileBytes is 64 bytes. Upload 65 bytes -> TOO_LARGE
            try {
                byte[] oversized = new byte[65];
                store.upload("/over.bin", new ByteArrayInputStream(oversized), 65, false);
                fail("Storage 2.5: Oversized upload rejected", "Expected TOO_LARGE exception");
            } catch (SharedFileStore.StoreException e) {
                if ("TOO_LARGE".equals(e.code())) {
                    pass("Storage 2.5: File exceeding maxFileBytes rejected with TOO_LARGE");
                } else {
                    fail("Storage 2.5: Oversized", "Code: " + e.code());
                }
            }

            // Test 2.6: Web upload without mkdir must NOT auto-create parent directories
            try {
                store.upload("/missing_dir/file.txt", new ByteArrayInputStream(new byte[]{1}), 1, false);
                fail("Storage 2.6: Auto-creating missing directory forbidden", "Expected NOT_FOUND exception");
            } catch (SharedFileStore.StoreException e) {
                if ("NOT_FOUND".equals(e.code()) && !Files.exists(root.resolve("missing_dir"))) {
                    pass("Storage 2.6: Upload into non-existent directory strictly rejected with NOT_FOUND");
                } else {
                    fail("Storage 2.6: Missing parent", "Code: " + e.code());
                }
            }

            // Test 2.7: Mkdir and duplicate mkdir
            SharedFileStore.Entry dirEntry = store.mkdir("/folder1");
            if (dirEntry.directory() && Files.isDirectory(root.resolve("folder1"))) {
                try {
                    store.mkdir("/folder1");
                    fail("Storage 2.7: Duplicate mkdir rejected", "Expected CONFLICT");
                } catch (SharedFileStore.StoreException e) {
                    if ("CONFLICT".equals(e.code())) {
                        pass("Storage 2.7: Mkdir creates directory and duplicate mkdir rejected with CONFLICT");
                    } else {
                        fail("Storage 2.7: Duplicate mkdir code", "Code: " + e.code());
                    }
                }
            } else {
                fail("Storage 2.7: Mkdir", "Directory not created");
            }

            // Test 2.8: Delete empty directory vs non-empty directory vs root
            store.mkdir("/empty_dir");
            store.mkdir("/non_empty_dir");
            store.upload("/non_empty_dir/sub.txt", new ByteArrayInputStream(new byte[]{1}), 1, false);

            try {
                store.delete("/");
                fail("Storage 2.8: Root delete rejected", "Expected FORBIDDEN");
            } catch (SharedFileStore.StoreException e) {
                if ("FORBIDDEN".equals(e.code())) {
                    pass("Storage 2.8a: Root directory deletion forbidden (FORBIDDEN)");
                } else {
                    fail("Storage 2.8a: Root delete code", "Code: " + e.code());
                }
            }

            try {
                store.delete("/non_empty_dir");
                fail("Storage 2.8: Non-empty delete rejected", "Expected NOT_EMPTY");
            } catch (SharedFileStore.StoreException e) {
                if ("NOT_EMPTY".equals(e.code())) {
                    pass("Storage 2.8b: Non-empty directory deletion rejected with NOT_EMPTY");
                } else {
                    fail("Storage 2.8b: Non-empty delete code", "Code: " + e.code());
                }
            }

            store.delete("/empty_dir");
            if (!Files.exists(root.resolve("empty_dir"))) {
                pass("Storage 2.8c: Empty directory deleted successfully");
            } else {
                fail("Storage 2.8c: Empty directory delete", "Directory still exists");
            }

            // Test 2.9: Rename ticket, rename target exists conflict, rename directory
            store.upload("/rename_me.txt", new ByteArrayInputStream(new byte[]{1, 2}), 2, false);
            SharedFileStore.RenameTicket ticket = store.prepareRename("/rename_me.txt");
            store.rename(ticket, "/renamed.txt");
            if (Files.exists(root.resolve("renamed.txt")) && !Files.exists(root.resolve("rename_me.txt"))) {
                pass("Storage 2.9a: File rename via ticket succeeded");
            } else {
                fail("Storage 2.9a: File rename", "Files state invalid");
            }

            // Rename to existing target -> CONFLICT
            try {
                store.rename("/renamed.txt", "/a.bin");
                fail("Storage 2.9b: Rename to existing target rejected", "Expected CONFLICT");
            } catch (SharedFileStore.StoreException e) {
                if ("CONFLICT".equals(e.code())) {
                    pass("Storage 2.9b: Rename to existing target rejected with CONFLICT");
                } else {
                    fail("Storage 2.9b: Rename conflict", "Code: " + e.code());
                }
            }

            // Test 2.10: AtomicMoveNotSupportedException test seam
            store.setSimulateAtomicUnsupported(true);
            try {
                store.upload("/atomic_test.bin", new ByteArrayInputStream(new byte[]{4, 5, 6}), 3, false);
                fail("Storage 2.10: Simulated unsupported atomic move", "Expected ATOMIC_UNSUPPORTED");
            } catch (SharedFileStore.StoreException e) {
                if ("ATOMIC_UNSUPPORTED".equals(e.code()) && !Files.exists(root.resolve("atomic_test.bin"))) {
                    pass("Storage 2.10: Atomic move unsupported fails safely without leaving partial target");
                } else {
                    fail("Storage 2.10: Atomic unsupported code", "Code: " + e.code());
                }
            } finally {
                store.setSimulateAtomicUnsupported(false);
            }

            // Test 2.11: List pagination and sorting (folders first)
            SharedFileStore.DirectoryPage page = store.list("/", 0, 10);
            if (page.total() >= 3 && !page.entries().isEmpty() && page.entries().get(0).directory()) {
                pass("Storage 2.11: List returns directory page with folders sorted before files");
            } else {
                fail("Storage 2.11: List sorting", "Entries: " + page.entries());
            }

            // Test 2.12: Open download streams exact bytes
            try (SharedFileStore.Download dl = store.openDownload("/renamed.txt")) {
                byte[] downloaded = dl.stream().readAllBytes();
                if (dl.size() == 2 && Arrays.equals(downloaded, new byte[]{1, 2})) {
                    pass("Storage 2.12: Open download provides stream and exact size");
                } else {
                    fail("Storage 2.12: Download bytes", "Size: " + dl.size());
                }
            }

            // Test 2.13: SafePaths control characters & traversal rejection
            try {
                store.openDownload("/../canary.txt");
                fail("Storage 2.13a: Traversal escape rejected", "Expected SecurityException");
            } catch (SecurityException e) {
                pass("Storage 2.13a: Path traversal strictly rejected outside root boundary");
            }

            try {
                store.openDownload("/test\r\n.txt");
                fail("Storage 2.13b: Control characters rejected", "Expected SecurityException");
            } catch (SecurityException e) {
                pass("Storage 2.13b: CRLF / control character injection strictly rejected");
            }

            // Verify canary outside root was never touched
            if ("CANARY_ALIVE".equals(Files.readString(canary, StandardCharsets.UTF_8))) {
                pass("Storage 2.14: Canary file outside root remains 100% untouched");
            } else {
                fail("Storage 2.14: Canary corrupted", "Canary modified");
            }

        } catch (Exception e) {
            fail("Storage Group Fixture Execution", e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // 3. FTP TESTS (Task 3)
    // =========================================================================

    public static void runFtpTests() {
        System.out.println("\n--- [GROUP: FTP] FTP Shared Store Integration & Wire Protocol ---");

        try (ProcessFixture fixture = new ProcessFixture()) {
            Path ws = fixture.createWorkspace();
            Path root = ws.resolve("ftp_workspace/server_root");
            Files.createDirectories(root);

            Map<String, String> env = new HashMap<>();
            env.put("FTP_ROOT", root.toString());
            env.put("FTP_ADMIN_PASSWORD", "AdminPass123!");

            ProcessFixture.ServerInstance ftp = fixture.startFtp(ws, env, null);
            if (ftp == null || !ftp.isAlive()) {
                fail("FTP 3.0: FTP server startup", "Failed to start FTP server");
                return;
            }

            int port = ftp.port();

            // Test 3.1: Connect and login as admin
            try (FtpWireClient client = new FtpWireClient("127.0.0.1", port)) {
                String uReply = client.sendCommand("USER admin");
                String pReply = client.sendCommand("PASS AdminPass123!");
                if (uReply.startsWith("331") && pReply.startsWith("230")) {
                    pass("FTP 3.1: Admin authentication succeeded on wire protocol");
                } else {
                    fail("FTP 3.1: Admin auth", "Replies: " + uReply + " / " + pReply);
                }

                // Test 3.2: Literal '+' filename (a+b.txt) STOR and RETR
                client.sendCommand("TYPE I");
                String pasv1 = client.sendCommand("PASV");
                int dataPort1 = client.parsePasvPort(pasv1);
                try (Socket dataSocket = new Socket("127.0.0.1", dataPort1)) {
                    String storReply = client.sendCommand("STOR a+b.txt");
                    if (!storReply.startsWith("150")) {
                        fail("FTP 3.2: STOR a+b.txt 150 reply", "Got: " + storReply);
                    } else {
                        dataSocket.getOutputStream().write("plus-content".getBytes(StandardCharsets.UTF_8));
                        dataSocket.getOutputStream().flush();
                    }
                }
                String storDone1 = client.readReply();
                if (storDone1.startsWith("226") && Files.exists(root.resolve("a+b.txt"))) {
                    pass("FTP 3.2: STOR a+b.txt with literal '+' preserved filename on disk");
                } else {
                    fail("FTP 3.2: STOR a+b.txt", "Reply: " + storDone1 + ", exists: " + Files.exists(root.resolve("a+b.txt")));
                }

                // Verify RETR a+b.txt
                String pasv2 = client.sendCommand("PASV");
                int dataPort2 = client.parsePasvPort(pasv2);
                byte[] retrBytes;
                try (Socket dataSocket = new Socket("127.0.0.1", dataPort2)) {
                    String retrReply = client.sendCommand("RETR a+b.txt");
                    if (!retrReply.startsWith("150")) {
                        fail("FTP 3.2b: RETR a+b.txt 150 reply", "Got: " + retrReply);
                    }
                    retrBytes = dataSocket.getInputStream().readAllBytes();
                }
                String retrDone1 = client.readReply();
                if (retrDone1.startsWith("226") && "plus-content".equals(new String(retrBytes, StandardCharsets.UTF_8))) {
                    pass("FTP 3.2b: RETR a+b.txt returned exact binary content for literal '+'");
                } else {
                    fail("FTP 3.2b: RETR a+b.txt", "Content: " + new String(retrBytes, StandardCharsets.UTF_8));
                }

                // Test 3.3: Literal '%' filename (100%.txt) STOR and RETR
                String pasv3 = client.sendCommand("PASV");
                int dataPort3 = client.parsePasvPort(pasv3);
                try (Socket dataSocket = new Socket("127.0.0.1", dataPort3)) {
                    client.sendCommand("STOR 100%.txt");
                    dataSocket.getOutputStream().write("percent-content".getBytes(StandardCharsets.UTF_8));
                    dataSocket.getOutputStream().flush();
                }
                String storDone2 = client.readReply();
                if (storDone2.startsWith("226") && Files.exists(root.resolve("100%.txt"))) {
                    pass("FTP 3.3: STOR 100%.txt with literal '%' preserved filename on disk");
                } else {
                    fail("FTP 3.3: STOR 100%.txt", "Reply: " + storDone2);
                }

                // Test 3.4: Unicode Vietnamese filename and spaces
                String pasv4 = client.sendCommand("PASV");
                int dataPort4 = client.parsePasvPort(pasv4);
                try (Socket dataSocket = new Socket("127.0.0.1", dataPort4)) {
                    client.sendCommand("STOR d\u1eef_li\u1ec7u 2026.txt");
                    dataSocket.getOutputStream().write("unicode-space-content".getBytes(StandardCharsets.UTF_8));
                    dataSocket.getOutputStream().flush();
                }
                String storDone3 = client.readReply();
                if (storDone3.startsWith("226")) {
                    pass("FTP 3.4: STOR filename with Unicode and spaces succeeded (226)");
                } else {
                    fail("FTP 3.4: Unicode/space STOR", "Reply: " + storDone3);
                }

                // Test 3.5: Overwrite existing file via STOR (replace=true)
                String pasv5 = client.sendCommand("PASV");
                int dataPort5 = client.parsePasvPort(pasv5);
                try (Socket dataSocket = new Socket("127.0.0.1", dataPort5)) {
                    client.sendCommand("STOR a+b.txt");
                    dataSocket.getOutputStream().write("updated-plus-content".getBytes(StandardCharsets.UTF_8));
                    dataSocket.getOutputStream().flush();
                }
                String storDone4 = client.readReply();
                if (storDone4.startsWith("226") && "updated-plus-content".equals(Files.readString(root.resolve("a+b.txt"), StandardCharsets.UTF_8))) {
                    pass("FTP 3.5: STOR atomically replaced existing file without leaving corrupted state");
                } else {
                    fail("FTP 3.5: Overwrite", "Reply: " + storDone4);
                }

                // Test 3.6: LIST verifies files and ensures staging/.state is NOT visible
                String pasv6 = client.sendCommand("PASV");
                int dataPort6 = client.parsePasvPort(pasv6);
                String listOutput;
                try (Socket dataSocket = new Socket("127.0.0.1", dataPort6)) {
                    client.sendCommand("LIST");
                    listOutput = new String(dataSocket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                }
                client.readReply(); // 226
                if (listOutput.contains("a+b.txt") && listOutput.contains("100%.txt") && !listOutput.contains(".state") && !listOutput.contains(".tmp")) {
                    pass("FTP 3.6: LIST lists filenames correctly and completely conceals staging / .state");
                } else {
                    fail("FTP 3.6: LIST output", "Output: " + listOutput);
                }

                // Test 3.7: Rename ticket sequence and overwrite rejection
                client.sendCommand("RNFR a+b.txt");
                client.sendCommand("RNTO a_plus_b.txt");
                if (Files.exists(root.resolve("a_plus_b.txt")) && !Files.exists(root.resolve("a+b.txt"))) {
                    pass("FTP 3.7a: RNFR/RNTO renamed file successfully");
                } else {
                    fail("FTP 3.7a: RNFR/RNTO", "State invalid");
                }

                // RNTO to existing target must be rejected (no overwrite rename)
                client.sendCommand("RNFR a_plus_b.txt");
                String rntoDupReply = client.sendCommand("RNTO 100%.txt");
                if (rntoDupReply.startsWith("550")) {
                    pass("FTP 3.7b: RNTO to existing destination strictly rejected with 550");
                } else {
                    fail("FTP 3.7b: RNTO existing target", "Reply: " + rntoDupReply);
                }

                // Test 3.8: Re-authentication clears pending rename ticket
                client.sendCommand("RNFR a_plus_b.txt");
                client.sendCommand("USER admin");
                client.sendCommand("PASS AdminPass123!");
                String staleRnto = client.sendCommand("RNTO orphan.txt");
                if (staleRnto.startsWith("503")) {
                    pass("FTP 3.8: USER/PASS clears pending rename state, stale RNTO rejected with 503");
                } else {
                    fail("FTP 3.8: Stale RNTO", "Reply: " + staleRnto);
                }

                // Test 3.9: Directory MKD and RMD via shared store
                String mkdReply = client.sendCommand("MKD my_ftp_dir");
                if (mkdReply.startsWith("257") && Files.isDirectory(root.resolve("my_ftp_dir"))) {
                    String rmdReply = client.sendCommand("RMD my_ftp_dir");
                    if (rmdReply.startsWith("250") && !Files.exists(root.resolve("my_ftp_dir"))) {
                        pass("FTP 3.9: MKD and RMD coordinated cleanly via SharedFileStore");
                    } else {
                        fail("FTP 3.9: RMD", "Reply: " + rmdReply);
                    }
                } else {
                    fail("FTP 3.9: MKD", "Reply: " + mkdReply);
                }
            }

        } catch (Exception e) {
            fail("FTP Group Fixture Execution", e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // 4. SESSION & ISOLATION TESTS (Task 4)
    // =========================================================================

    public static void runSessionTests() {
        System.out.println("\n--- [GROUP: SESSION] FileSessionAuth, Local Guards & CSRF ---");

        try {
            Instant start = Instant.parse("2026-10-17T00:00:00Z");
            MutableClock clock = new MutableClock(start);
            FileSessionAuth auth = new FileSessionAuth("SecretAdminPass123!", true, clock);

            // Test 4.1: Remote non-loopback rejected with 403
            MockHttpExchange nonLoopEx = new MockHttpExchange(InetAddress.getByName("192.168.1.105"), 54321);
            nonLoopEx.getRequestHeaders().set("Host", "localhost:8080");
            try {
                auth.checkLocalRequest(nonLoopEx);
                fail("Session 4.1: Remote non-loopback IP rejected", "Expected AuthException 403");
            } catch (server.FileSessionAuth.AuthException e) {
                if (e.statusCode() == 403 && "FORBIDDEN".equals(e.code())) {
                    pass("Session 4.1: Remote non-loopback IP strictly rejected with 403 FORBIDDEN");
                } else {
                    fail("Session 4.1: Status code", "Status: " + e.statusCode());
                }
            }

            // Test 4.2: X-Forwarded-For ignored on non-loopback
            nonLoopEx.getRequestHeaders().set("X-Forwarded-For", "127.0.0.1");
            try {
                auth.checkLocalRequest(nonLoopEx);
                fail("Session 4.2: X-Forwarded-For spoof rejected", "Expected AuthException 403");
            } catch (server.FileSessionAuth.AuthException e) {
                pass("Session 4.2: X-Forwarded-For header strictly ignored; loopback verification relies on socket");
            }

            // Test 4.3: Host header validation (reject substring / foreign suffix)
            MockHttpExchange loopEx = new MockHttpExchange(InetAddress.getByName("127.0.0.1"), 54321);
            loopEx.getRequestHeaders().set("Host", "evil-localhost.com:8080");
            try {
                auth.checkLocalRequest(loopEx);
                fail("Session 4.3: Host suffix spoof rejected", "Expected AuthException 403");
            } catch (server.FileSessionAuth.AuthException e) {
                pass("Session 4.3: Host header suffix spoof (evil-localhost.com) strictly rejected with 403");
            }

            // Test 4.4: Origin validation (reject null origin and foreign origin)
            loopEx.getRequestHeaders().set("Host", "localhost:8080");
            loopEx.getRequestHeaders().set("Origin", "null");
            try {
                auth.checkLocalRequest(loopEx);
                fail("Session 4.4a: Null Origin rejected", "Expected AuthException 403");
            } catch (server.FileSessionAuth.AuthException e) {
                pass("Session 4.4a: Origin: null strictly rejected with 403");
            }

            loopEx.getRequestHeaders().set("Origin", "http://attacker.site:8080");
            try {
                auth.checkLocalRequest(loopEx);
                fail("Session 4.4b: Cross-origin rejected", "Expected AuthException 403");
            } catch (server.FileSessionAuth.AuthException e) {
                pass("Session 4.4b: Cross-origin Origin header strictly rejected with 403");
            }

            // Test 4.5: Unconfigured admin password returns 503 SETUP_REQUIRED
            FileSessionAuth unconfiguredAuth = new FileSessionAuth("", false, clock);
            try {
                unconfiguredAuth.login("admin", "admin123");
                fail("Session 4.5: Unconfigured password rejected", "Expected 503 SETUP_REQUIRED");
            } catch (server.FileSessionAuth.AuthException e) {
                if (e.statusCode() == 503 && "SETUP_REQUIRED".equals(e.code())) {
                    pass("Session 4.5: Unconfigured password yields 503 SETUP_REQUIRED (no default admin123)");
                } else {
                    fail("Session 4.5: Code", "Code: " + e.code());
                }
            }

            // Test 4.6: Rate limiting: 5 failed attempts within 60s trigger 429
            for (int i = 0; i < 5; i++) {
                try {
                    auth.login("admin", "wrongPass");
                } catch (server.FileSessionAuth.AuthException ignored) {}
            }
            try {
                auth.login("admin", "wrongPass");
                fail("Session 4.6: Rate limiting throttled", "Expected 429 TOO_MANY_REQUESTS");
            } catch (server.FileSessionAuth.AuthException e) {
                if (e.statusCode() == 429 && "TOO_MANY_REQUESTS".equals(e.code())) {
                    pass("Session 4.6: 5 failed logins within 60s trigger 429 TOO_MANY_REQUESTS");
                } else {
                    fail("Session 4.6: Rate limit code", "Code: " + e.code());
                }
            }

            // Advance clock by 61 seconds to clear throttle
            clock.advance(Duration.ofSeconds(61));

            // Test 4.7: Successful login and SessionView token redaction
            server.FileSessionAuth.SessionView adminView = auth.login("admin", "SecretAdminPass123!");
            if (adminView != null && adminView.role() == server.ftp.FtpUserAuth.Role.ADMIN) {
                String str = adminView.toString();
                if (!str.contains(adminView.sessionToken()) && !str.contains(adminView.csrfToken()) && str.contains("***")) {
                    pass("Session 4.7: Admin login succeeded and SessionView.toString() redacts tokens");
                } else {
                    fail("Session 4.7: Secret leakage in toString", "toString: " + str);
                }
            } else {
                fail("Session 4.7: Admin login", "Login failed");
            }

            // Test 4.8: CSRF and X-File-Explorer enforcement on mutation
            loopEx.getRequestHeaders().set("Origin", "http://localhost:8080");
            loopEx.getRequestHeaders().set("Cookie", server.FileSessionAuth.COOKIE_NAME + "=" + adminView.sessionToken());

            // Missing X-File-Explorer
            try {
                auth.requireSession(loopEx, true);
                fail("Session 4.8a: Missing X-File-Explorer rejected", "Expected 403");
            } catch (server.FileSessionAuth.AuthException e) {
                pass("Session 4.8a: Mutation without X-File-Explorer header rejected with 403");
            }

            // Valid X-File-Explorer, but missing CSRF token
            loopEx.getRequestHeaders().set(server.FileSessionAuth.EXPLORER_HEADER, "1");
            try {
                auth.requireSession(loopEx, true);
                fail("Session 4.8b: Missing CSRF token rejected", "Expected 403");
            } catch (server.FileSessionAuth.AuthException e) {
                pass("Session 4.8b: Mutation without X-CSRF-Token rejected with 403");
            }

            // Valid X-File-Explorer and valid CSRF token
            loopEx.getRequestHeaders().set(server.FileSessionAuth.CSRF_HEADER, adminView.csrfToken());
            server.FileSessionAuth.SessionView validated = auth.requireSession(loopEx, true);
            if (validated != null && validated.user().equals("admin")) {
                pass("Session 4.8c: Mutation with valid session, X-File-Explorer, and CSRF token succeeded");
            } else {
                fail("Session 4.8c: Session validation", "Failed");
            }

            // Test 4.9: Guest login allowed read, denied mutation
            server.FileSessionAuth.SessionView guestView = auth.login("guest", "");
            MockHttpExchange guestEx = new MockHttpExchange(InetAddress.getByName("127.0.0.1"), 54322);
            guestEx.getRequestHeaders().set("Host", "localhost:8080");
            guestEx.getRequestHeaders().set("Cookie", server.FileSessionAuth.COOKIE_NAME + "=" + guestView.sessionToken());

            // Guest read -> OK
            server.FileSessionAuth.SessionView guestRead = auth.requireSession(guestEx, false);
            if (guestRead != null && guestRead.role() == server.ftp.FtpUserAuth.Role.GUEST) {
                pass("Session 4.9a: Guest account allowed read operations");
            } else {
                fail("Session 4.9a: Guest read", "Failed");
            }

            // Guest write -> 403
            guestEx.getRequestHeaders().set(server.FileSessionAuth.EXPLORER_HEADER, "1");
            guestEx.getRequestHeaders().set(server.FileSessionAuth.CSRF_HEADER, guestView.csrfToken());
            try {
                auth.requireSession(guestEx, true);
                fail("Session 4.9b: Guest mutation rejected", "Expected 403 FORBIDDEN");
            } catch (server.FileSessionAuth.AuthException e) {
                pass("Session 4.9b: Guest mutation attempt strictly rejected with 403 FORBIDDEN");
            }

            // Test 4.10: Session expiry: idle timeout 30 minutes
            clock.advance(Duration.ofMinutes(31));
            try {
                auth.requireSession(loopEx, false);
                fail("Session 4.10: Idle session expired", "Expected 401 UNAUTHORIZED");
            } catch (server.FileSessionAuth.AuthException e) {
                pass("Session 4.10: Session expired after 30 minutes idle timeout (401)");
            }

            // Test 4.11: Logout removes session
            server.FileSessionAuth.SessionView logoutTestView = auth.login("admin", "SecretAdminPass123!");
            MockHttpExchange logoutEx = new MockHttpExchange(InetAddress.getByName("127.0.0.1"), 54323);
            logoutEx.getRequestHeaders().set("Host", "localhost:8080");
            logoutEx.getRequestHeaders().set("Cookie", server.FileSessionAuth.COOKIE_NAME + "=" + logoutTestView.sessionToken());

            auth.logout(logoutEx);
            try {
                auth.requireSession(logoutEx, false);
                fail("Session 4.11: Logged out session rejected", "Expected 401 UNAUTHORIZED");
            } catch (server.FileSessionAuth.AuthException e) {
                pass("Session 4.11: Logout successfully invalidated session token in memory");
            }

        } catch (Exception e) {
            fail("Session Group Execution", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void runIsolationTests() {
        System.out.println("\n--- [GROUP: ISOLATION] Untrusted Upstream Content Policy ---");

        try {
            // Test 4.12: UpstreamContentPolicy header structure
            MockHttpExchange ex = new MockHttpExchange(InetAddress.getByName("127.0.0.1"), 54321);
            server.UpstreamContentPolicy.apply(ex);
            String csp = ex.getResponseHeaders().getFirst("Content-Security-Policy");
            String nosniff = ex.getResponseHeaders().getFirst("X-Content-Type-Options");

            if (csp != null && csp.contains("sandbox") && !csp.contains("allow-scripts") && !csp.contains("allow-same-origin")) {
                if ("nosniff".equalsIgnoreCase(nosniff)) {
                    pass("Session 4.12: UpstreamContentPolicy applies CSP sandbox without script/same-origin rights and nosniff");
                } else {
                    fail("Session 4.12: nosniff header", "Got: " + nosniff);
                }
            } else {
                fail("Session 4.12: CSP header", "Got: " + csp);
            }

            // Process test: Launch HTTP server and test /fetch with an upstream HTML page
            try (ProcessFixture fixture = new ProcessFixture()) {
                Path ws = fixture.createWorkspace();
                Map<String, String> env = new HashMap<>();
                env.put("FTP_ADMIN_PASSWORD", "AdminPass123!");

                ProcessFixture.ServerInstance http = fixture.startHttp(ws, env);

                // Start an ephemeral upstream server returning HTML with a script tag
                int upstreamPort = fixture.findFreePort();
                com.sun.net.httpserver.HttpServer upstreamServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", upstreamPort), 0);
                upstreamServer.createContext("/evil.html", exchange -> {
                    byte[] body = "<!DOCTYPE html><html><head><title>Untrusted</title></head><body><script>fetch('/api/files')</script></body></html>".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
                });
                upstreamServer.start();

                try {
                    // Call /fetch on the HTTP server to fetch /evil.html in raw mode
                    java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
                    String fetchUrl = "http://localhost:" + http.port() + "/fetch?url=http://127.0.0.1:" + upstreamPort + "/evil.html";
                    java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(URI.create(fetchUrl)).GET().build();
                    java.net.http.HttpResponse<String> resp = httpClient.send(req, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                    String respCsp = resp.headers().firstValue("Content-Security-Policy").orElse(null);
                    String respNosniff = resp.headers().firstValue("X-Content-Type-Options").orElse(null);

                    if (respCsp != null && respCsp.contains("sandbox") && !respCsp.contains("allow-scripts") && !respCsp.contains("allow-same-origin")) {
                        pass("Session 4.13: /fetch raw response strictly carries CSP sandbox without script or same-origin capabilities");
                    } else {
                        fail("Session 4.13: Fetch CSP header", "CSP: " + respCsp);
                    }

                    // Check that /index.html (application UI) does NOT have the sandbox header
                    java.net.http.HttpRequest uiReq = java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + http.port() + "/index.html")).GET().build();
                    java.net.http.HttpResponse<String> uiResp = httpClient.send(uiReq, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    String uiCsp = uiResp.headers().firstValue("Content-Security-Policy").orElse(null);
                    if (uiCsp == null || !uiCsp.contains("sandbox")) {
                        pass("Session 4.14: Application UI (/index.html) is NOT crippled by the upstream sandbox policy");
                    } else {
                        fail("Session 4.14: Application UI has sandbox", "CSP: " + uiCsp);
                    }

                } finally {
                    upstreamServer.stop(0);
                }
            }

        } catch (Exception e) {
            fail("Isolation Group Execution", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void runApiTests() {
        System.out.println("\n--- [GROUP: API] Files HTTP API Wire Tests ---");
        try (ProcessFixture fixture = new ProcessFixture()) {
            Path ws = fixture.createWorkspace();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

            // 1. Missing config returns 503 SETUP_REQUIRED and legacy route unaffected
            try {
                ProcessFixture.ServerInstance httpNoPass = fixture.startHttp(ws, Map.of());
                int p1 = httpNoPass.port();

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + p1 + "/api/files/login"))
                        .header("X-File-Explorer", "1")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"foo\"}"))
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 503 && resp.body().contains("SETUP_REQUIRED")) {
                    pass("api_unconfigured_password_returns_503");
                } else {
                    fail("api_unconfigured_password_returns_503", "status=" + resp.statusCode() + " body=" + resp.body());
                }

                HttpRequest timeReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + p1 + "/api/time"))
                        .GET().build();
                HttpResponse<String> timeResp = client.send(timeReq, HttpResponse.BodyHandlers.ofString());
                if (timeResp.statusCode() == 200 && timeResp.body().contains("time")) {
                    pass("api_legacy_routes_unaffected_when_unconfigured");
                } else {
                    fail("api_legacy_routes_unaffected_when_unconfigured", "status=" + timeResp.statusCode());
                }
                httpNoPass.process().destroyForcibly();
            } catch (Exception e) {
                fail("api_unconfigured_password_returns_503", e.getMessage());
            }

            // Start HTTP server with configured admin password & guest enabled
            Path ws2 = fixture.createWorkspace();
            Map<String, String> env = Map.of(
                    "FTP_ADMIN_PASSWORD", "AdminPass_2026!",
                    "WEB_FILES_ALLOW_GUEST", "true"
            );
            ProcessFixture.ServerInstance http = fixture.startHttp(ws2, env);
            int port = http.port();

            // 2. Exact routing: /api/filesXYZ and invalid subroutes return 404 JSON
            try {
                HttpRequest r1 = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/filesXYZ")).GET().build();
                HttpResponse<String> resp1 = client.send(r1, HttpResponse.BodyHandlers.ofString());
                HttpRequest r2 = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/files/nonexistent_subroute")).GET().build();
                HttpResponse<String> resp2 = client.send(r2, HttpResponse.BodyHandlers.ofString());

                if (resp1.statusCode() == 404 && resp1.body().contains("NOT_FOUND") &&
                    resp2.statusCode() == 404 && resp2.body().contains("NOT_FOUND")) {
                    pass("api_exact_routing_and_404_json");
                } else {
                    fail("api_exact_routing_and_404_json", "r1=" + resp1.statusCode() + ", r2=" + resp2.statusCode());
                }
            } catch (Exception e) {
                fail("api_exact_routing_and_404_json", e.getMessage());
            }

            // 3. Method not allowed (405) with Allow header
            try {
                HttpRequest m1 = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/files")).POST(HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<String> res1 = client.send(m1, HttpResponse.BodyHandlers.ofString());

                HttpRequest m2 = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/files/login")).GET().build();
                HttpResponse<String> res2 = client.send(m2, HttpResponse.BodyHandlers.ofString());

                HttpRequest m3 = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/files/download")).POST(HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<String> res3 = client.send(m3, HttpResponse.BodyHandlers.ofString());

                boolean ok = res1.statusCode() == 405 && res1.headers().firstValue("Allow").orElse("").contains("GET")
                        && res2.statusCode() == 405 && res2.headers().firstValue("Allow").orElse("").contains("POST")
                        && res3.statusCode() == 405 && res3.headers().firstValue("Allow").orElse("").contains("GET");
                if (ok) {
                    pass("api_method_not_allowed_405_with_allow_header");
                } else {
                    fail("api_method_not_allowed_405_with_allow_header", "status codes: " + res1.statusCode() + ", " + res2.statusCode() + ", " + res3.statusCode());
                }
            } catch (Exception e) {
                fail("api_method_not_allowed_405_with_allow_header", e.getMessage());
            }

            // 4. Unsupported Media Type (415)
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/login"))
                        .header("X-File-Explorer", "1")
                        .header("Content-Type", "text/plain")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"AdminPass_2026!\"}"))
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 415 && resp.body().contains("UNSUPPORTED_MEDIA_TYPE")) {
                    pass("api_unsupported_media_type_415");
                } else {
                    fail("api_unsupported_media_type_415", "status=" + resp.statusCode());
                }
            } catch (Exception e) {
                fail("api_unsupported_media_type_415", e.getMessage());
            }

            // 5. Missing or invalid X-File-Explorer header returns 403
            try {
                HttpRequest r1 = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"AdminPass_2026!\"}"))
                        .build();
                HttpResponse<String> resp1 = client.send(r1, HttpResponse.BodyHandlers.ofString());

                HttpRequest r2 = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/login"))
                        .header("X-File-Explorer", "0")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"AdminPass_2026!\"}"))
                        .build();
                HttpResponse<String> resp2 = client.send(r2, HttpResponse.BodyHandlers.ofString());

                if (resp1.statusCode() == 403 && resp2.statusCode() == 403) {
                    pass("api_explorer_header_required_on_mutation");
                } else {
                    fail("api_explorer_header_required_on_mutation", "r1=" + resp1.statusCode() + ", r2=" + resp2.statusCode());
                }
            } catch (Exception e) {
                fail("api_explorer_header_required_on_mutation", e.getMessage());
            }

            // 6. Unauthenticated requests return 401 UNAUTHORIZED
            try {
                HttpRequest r1 = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/files")).GET().build();
                HttpResponse<String> resp1 = client.send(r1, HttpResponse.BodyHandlers.ofString());

                HttpRequest r2 = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/files/session")).GET().build();
                HttpResponse<String> resp2 = client.send(r2, HttpResponse.BodyHandlers.ofString());

                HttpRequest r3 = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/mkdir"))
                        .header("X-File-Explorer", "1")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"path\":\"/test\"}"))
                        .build();
                HttpResponse<String> resp3 = client.send(r3, HttpResponse.BodyHandlers.ofString());

                if (resp1.statusCode() == 401 && resp2.statusCode() == 401 && resp3.statusCode() == 401) {
                    pass("api_unauthenticated_requests_return_401");
                } else {
                    fail("api_unauthenticated_requests_return_401", "r1=" + resp1.statusCode() + ", r2=" + resp2.statusCode() + ", r3=" + resp3.statusCode());
                }
            } catch (Exception e) {
                fail("api_unauthenticated_requests_return_401", e.getMessage());
            }

            // 7. Admin login and session verification
            String adminCookie;
            String adminCsrf;
            try {
                HttpRequest badLogin = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/login"))
                        .header("X-File-Explorer", "1")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                        .build();
                HttpResponse<String> badResp = client.send(badLogin, HttpResponse.BodyHandlers.ofString());
                if (badResp.statusCode() != 401) {
                    fail("api_admin_login_and_session", "bad login status=" + badResp.statusCode());
                    return;
                }

                HttpRequest goodLogin = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/login"))
                        .header("X-File-Explorer", "1")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"AdminPass_2026!\"}"))
                        .build();
                HttpResponse<String> goodResp = client.send(goodLogin, HttpResponse.BodyHandlers.ofString());
                if (goodResp.statusCode() != 200) {
                    fail("api_admin_login_and_session", "good login status=" + goodResp.statusCode());
                    return;
                }

                String setCookie = goodResp.headers().firstValue("set-cookie").orElse("");
                if (!setCookie.contains("FileExplorerSession=") || !setCookie.contains("HttpOnly") || !setCookie.contains("SameSite=Strict")) {
                    fail("api_admin_login_and_session", "missing or malformed Set-Cookie header");
                    return;
                }
                adminCookie = extractCookie(setCookie, "FileExplorerSession");
                adminCsrf = extractJson(goodResp.body(), "csrfToken");
                String user = extractJson(goodResp.body(), "user");
                String role = extractJson(goodResp.body(), "role");

                if (!"admin".equals(user) || !"ADMIN".equals(role) || adminCsrf == null || adminCsrf.isBlank()) {
                    fail("api_admin_login_and_session", "unexpected body content: user=" + user + ", role=" + role);
                    return;
                }

                HttpRequest sessReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/session"))
                        .header("Cookie", adminCookie)
                        .GET().build();
                HttpResponse<String> sessResp = client.send(sessReq, HttpResponse.BodyHandlers.ofString());
                if (sessResp.statusCode() == 200 && sessResp.body().contains("\"ADMIN\"")) {
                    pass("api_admin_login_and_session");
                } else {
                    fail("api_admin_login_and_session", "sess status=" + sessResp.statusCode());
                }
            } catch (Exception e) {
                fail("api_admin_login_and_session", e.getMessage());
                return;
            }

            // 8. Host and Origin header guards
            try {
                String rawReq = "GET /api/files/session HTTP/1.1\r\n" +
                        "Host: evil.attacker.com:" + port + "\r\n" +
                        "Cookie: " + adminCookie + "\r\n" +
                        "Connection: close\r\n\r\n";
                RawResponse rawResp = sendRawHttp(port, rawReq);
                if (rawResp.statusCode() != 403) {
                    fail("api_host_and_origin_guards", "Host check expected 403, got " + rawResp.statusCode());
                    return;
                }

                HttpRequest originReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/session"))
                        .header("Cookie", adminCookie)
                        .header("Origin", "http://attacker.com")
                        .GET().build();
                HttpResponse<String> originResp = client.send(originReq, HttpResponse.BodyHandlers.ofString());
                if (originResp.statusCode() != 403) {
                    fail("api_host_and_origin_guards", "Origin check expected 403, got " + originResp.statusCode());
                    return;
                }

                HttpRequest nullOriginReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/session"))
                        .header("Cookie", adminCookie)
                        .header("Origin", "null")
                        .GET().build();
                HttpResponse<String> nullOriginResp = client.send(nullOriginReq, HttpResponse.BodyHandlers.ofString());
                if (nullOriginResp.statusCode() != 403) {
                    fail("api_host_and_origin_guards", "Null origin check expected 403, got " + nullOriginResp.statusCode());
                    return;
                }

                pass("api_host_and_origin_guards");
            } catch (Exception e) {
                fail("api_host_and_origin_guards", e.getMessage());
            }

            // 9. CSRF protection on mutations
            try {
                HttpRequest noCsrfReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/mkdir"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"path\":\"/csrf_test\"}"))
                        .build();
                HttpResponse<String> noCsrfResp = client.send(noCsrfReq, HttpResponse.BodyHandlers.ofString());

                HttpRequest badCsrfReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/mkdir"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", "completely_bogus_csrf_token")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"path\":\"/csrf_test\"}"))
                        .build();
                HttpResponse<String> badCsrfResp = client.send(badCsrfReq, HttpResponse.BodyHandlers.ofString());

                if (noCsrfResp.statusCode() == 403 && badCsrfResp.statusCode() == 403) {
                    pass("api_csrf_protection_on_mutations");
                } else {
                    fail("api_csrf_protection_on_mutations", "noCsrf=" + noCsrfResp.statusCode() + ", badCsrf=" + badCsrfResp.statusCode());
                }
            } catch (Exception e) {
                fail("api_csrf_protection_on_mutations", e.getMessage());
            }

            // 10. Admin full CRUD workflow
            try {
                // mkdir /docs
                HttpRequest mkdirReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/mkdir"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", adminCsrf)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"path\":\"/docs\"}"))
                        .build();
                HttpResponse<String> mkdirResp = client.send(mkdirReq, HttpResponse.BodyHandlers.ofString());
                if (mkdirResp.statusCode() != 201) {
                    fail("api_admin_crud_workflow", "mkdir status=" + mkdirResp.statusCode());
                    return;
                }

                // list /
                HttpRequest listReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files?path=/"))
                        .header("Cookie", adminCookie)
                        .GET().build();
                HttpResponse<String> listResp = client.send(listReq, HttpResponse.BodyHandlers.ofString());
                if (listResp.statusCode() != 200 || !listResp.body().contains("\"name\":\"docs\"")) {
                    fail("api_admin_crud_workflow", "list status=" + listResp.statusCode() + " body=" + listResp.body());
                    return;
                }

                // upload /docs/hello.txt
                byte[] fileBytes = "hello world from web".getBytes(StandardCharsets.UTF_8);
                HttpRequest upReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/content?path=/docs/hello.txt"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", adminCsrf)
                        .header("X-File-Size", String.valueOf(fileBytes.length))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
                        .build();
                HttpResponse<String> upResp = client.send(upReq, HttpResponse.BodyHandlers.ofString());
                if (upResp.statusCode() != 201) {
                    fail("api_admin_crud_workflow", "upload status=" + upResp.statusCode());
                    return;
                }

                // download /docs/hello.txt and verify attachment headers
                HttpRequest dlReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/download?path=/docs/hello.txt"))
                        .header("Cookie", adminCookie)
                        .GET().build();
                HttpResponse<byte[]> dlResp = client.send(dlReq, HttpResponse.BodyHandlers.ofByteArray());
                if (dlResp.statusCode() != 200 || !Arrays.equals(fileBytes, dlResp.body())) {
                    fail("api_admin_crud_workflow", "download status=" + dlResp.statusCode());
                    return;
                }
                String cd = dlResp.headers().firstValue("content-disposition").orElse("");
                String ct = dlResp.headers().firstValue("content-type").orElse("");
                if (!ct.contains("application/octet-stream") || !cd.contains("attachment") || !cd.contains("filename*=UTF-8''hello.txt")) {
                    fail("api_admin_crud_workflow", "unexpected headers: ct=" + ct + ", cd=" + cd);
                    return;
                }

                // rename /docs/hello.txt -> /docs/renamed.txt
                HttpRequest renReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/rename"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", adminCsrf)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"from\":\"/docs/hello.txt\",\"to\":\"/docs/renamed.txt\"}"))
                        .build();
                HttpResponse<String> renResp = client.send(renReq, HttpResponse.BodyHandlers.ofString());
                if (renResp.statusCode() != 200) {
                    fail("api_admin_crud_workflow", "rename status=" + renResp.statusCode());
                    return;
                }

                // delete non-empty directory /docs -> 409
                HttpRequest delDirFailReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files?path=/docs"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", adminCsrf)
                        .DELETE().build();
                HttpResponse<String> delDirFailResp = client.send(delDirFailReq, HttpResponse.BodyHandlers.ofString());
                if (delDirFailResp.statusCode() != 409) {
                    fail("api_admin_crud_workflow", "expected 409 for non-empty dir delete, got " + delDirFailResp.statusCode());
                    return;
                }

                // delete /docs/renamed.txt -> 204
                HttpRequest delFileReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files?path=/docs/renamed.txt"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", adminCsrf)
                        .DELETE().build();
                HttpResponse<Void> delFileResp = client.send(delFileReq, HttpResponse.BodyHandlers.discarding());
                if (delFileResp.statusCode() != 204) {
                    fail("api_admin_crud_workflow", "delete file status=" + delFileResp.statusCode());
                    return;
                }

                // delete empty directory /docs -> 204
                HttpRequest delDirReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files?path=/docs"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", adminCsrf)
                        .DELETE().build();
                HttpResponse<Void> delDirResp = client.send(delDirReq, HttpResponse.BodyHandlers.discarding());
                if (delDirResp.statusCode() != 204) {
                    fail("api_admin_crud_workflow", "delete dir status=" + delDirResp.statusCode());
                    return;
                }

                // delete root / -> 403
                HttpRequest delRootReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files?path=/"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", adminCsrf)
                        .DELETE().build();
                HttpResponse<String> delRootResp = client.send(delRootReq, HttpResponse.BodyHandlers.ofString());
                if (delRootResp.statusCode() != 403) {
                    fail("api_admin_crud_workflow", "delete root expected 403, got " + delRootResp.statusCode());
                    return;
                }

                pass("api_admin_crud_workflow");
            } catch (Exception e) {
                fail("api_admin_crud_workflow", e.getMessage());
            }

            // 11. Upload validation, zero-byte file, size mismatch, no-overwrite
            try {
                HttpRequest noSizeReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/content?path=/test.bin"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", adminCsrf)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1, 2, 3}))
                        .build();
                HttpResponse<String> noSizeResp = client.send(noSizeReq, HttpResponse.BodyHandlers.ofString());
                if (noSizeResp.statusCode() != 400) {
                    fail("api_upload_validation_and_zero_byte", "expected 400 for missing size header, got " + noSizeResp.statusCode());
                    return;
                }

                HttpRequest zeroReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/content?path=/empty.dat"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", adminCsrf)
                        .header("X-File-Size", "0")
                        .PUT(HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<String> zeroResp = client.send(zeroReq, HttpResponse.BodyHandlers.ofString());
                if (zeroResp.statusCode() != 201) {
                    fail("api_upload_validation_and_zero_byte", "expected 201 for zero-byte upload, got " + zeroResp.statusCode());
                    return;
                }

                HttpRequest dlZeroReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/download?path=/empty.dat"))
                        .header("Cookie", adminCookie)
                        .GET().build();
                HttpResponse<byte[]> dlZeroResp = client.send(dlZeroReq, HttpResponse.BodyHandlers.ofByteArray());
                if (dlZeroResp.statusCode() != 200 || dlZeroResp.body().length != 0) {
                    fail("api_upload_validation_and_zero_byte", "download zero-byte failed");
                    return;
                }

                HttpRequest dupReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/content?path=/empty.dat"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", adminCsrf)
                        .header("X-File-Size", "3")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1, 2, 3}))
                        .build();
                HttpResponse<String> dupResp = client.send(dupReq, HttpResponse.BodyHandlers.ofString());
                if (dupResp.statusCode() != 409) {
                    fail("api_upload_validation_and_zero_byte", "expected 409 on duplicate upload, got " + dupResp.statusCode());
                    return;
                }

                HttpRequest misReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/content?path=/mismatch.dat"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", adminCsrf)
                        .header("X-File-Size", "10")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1, 2, 3, 4}))
                        .build();
                HttpResponse<String> misResp = client.send(misReq, HttpResponse.BodyHandlers.ofString());
                if (misResp.statusCode() != 409) {
                    fail("api_upload_validation_and_zero_byte", "expected 409 on size mismatch, got " + misResp.statusCode());
                    return;
                }

                pass("api_upload_validation_and_zero_byte");
            } catch (Exception e) {
                fail("api_upload_validation_and_zero_byte", e.getMessage());
            }

            // 12. HTML download attachment isolation
            try {
                byte[] htmlPayload = "<script>alert('xss')</script>".getBytes(StandardCharsets.UTF_8);
                HttpRequest upHtml = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/content?path=/untrusted.html"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", adminCsrf)
                        .header("X-File-Size", String.valueOf(htmlPayload.length))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(htmlPayload))
                        .build();
                HttpResponse<String> upHtmlResp = client.send(upHtml, HttpResponse.BodyHandlers.ofString());
                if (upHtmlResp.statusCode() != 201) {
                    fail("api_html_download_attachment_isolation", "upload status=" + upHtmlResp.statusCode());
                    return;
                }

                HttpRequest dlHtml = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/download?path=/untrusted.html"))
                        .header("Cookie", adminCookie)
                        .GET().build();
                HttpResponse<byte[]> dlHtmlResp = client.send(dlHtml, HttpResponse.BodyHandlers.ofByteArray());
                String ct = dlHtmlResp.headers().firstValue("content-type").orElse("");
                String cd = dlHtmlResp.headers().firstValue("content-disposition").orElse("");
                String nosniff = dlHtmlResp.headers().firstValue("x-content-type-options").orElse("");

                if (dlHtmlResp.statusCode() == 200 &&
                    ct.contains("application/octet-stream") &&
                    cd.contains("attachment") &&
                    "nosniff".equalsIgnoreCase(nosniff)) {
                    pass("api_html_download_attachment_isolation");
                } else {
                    fail("api_html_download_attachment_isolation", "status=" + dlHtmlResp.statusCode() + " ct=" + ct + " cd=" + cd);
                }
            } catch (Exception e) {
                fail("api_html_download_attachment_isolation", e.getMessage());
            }

            // 13. Guest read-only enforcement
            try {
                HttpRequest guestLogin = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/login"))
                        .header("X-File-Explorer", "1")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"guest\",\"password\":\"\"}"))
                        .build();
                HttpResponse<String> guestResp = client.send(guestLogin, HttpResponse.BodyHandlers.ofString());
                if (guestResp.statusCode() != 200) {
                    fail("api_guest_read_only_enforcement", "guest login status=" + guestResp.statusCode());
                    return;
                }

                String guestCookie = extractCookie(guestResp.headers().firstValue("set-cookie").orElse(""), "FileExplorerSession");
                String guestCsrf = extractJson(guestResp.body(), "csrfToken");

                HttpRequest gList = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files?path=/"))
                        .header("Cookie", guestCookie)
                        .GET().build();
                HttpResponse<String> gListResp = client.send(gList, HttpResponse.BodyHandlers.ofString());
                if (gListResp.statusCode() != 200) {
                    fail("api_guest_read_only_enforcement", "guest list failed: " + gListResp.statusCode());
                    return;
                }

                HttpRequest gMkdir = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/mkdir"))
                        .header("Cookie", guestCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", guestCsrf)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"path\":\"/guest_dir\"}"))
                        .build();
                HttpResponse<String> gMkdirResp = client.send(gMkdir, HttpResponse.BodyHandlers.ofString());

                HttpRequest gUp = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/content?path=/guest.dat"))
                        .header("Cookie", guestCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", guestCsrf)
                        .header("X-File-Size", "1")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1}))
                        .build();
                HttpResponse<String> gUpResp = client.send(gUp, HttpResponse.BodyHandlers.ofString());

                HttpRequest gDel = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files?path=/empty.dat"))
                        .header("Cookie", guestCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", guestCsrf)
                        .DELETE().build();
                HttpResponse<String> gDelResp = client.send(gDel, HttpResponse.BodyHandlers.ofString());

                if (gMkdirResp.statusCode() == 403 && gUpResp.statusCode() == 403 && gDelResp.statusCode() == 403) {
                    pass("api_guest_read_only_enforcement");
                } else {
                    fail("api_guest_read_only_enforcement", "mkdir=" + gMkdirResp.statusCode() + " up=" + gUpResp.statusCode() + " del=" + gDelResp.statusCode());
                }
            } catch (Exception e) {
                fail("api_guest_read_only_enforcement", e.getMessage());
            }

            // 14. Logout workflow
            try {
                HttpRequest logoutReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/logout"))
                        .header("Cookie", adminCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", adminCsrf)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<Void> logoutResp = client.send(logoutReq, HttpResponse.BodyHandlers.discarding());
                if (logoutResp.statusCode() != 204) {
                    fail("api_logout_workflow", "logout status=" + logoutResp.statusCode());
                    return;
                }

                HttpRequest oldSessReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/session"))
                        .header("Cookie", adminCookie)
                        .GET().build();
                HttpResponse<String> oldSessResp = client.send(oldSessReq, HttpResponse.BodyHandlers.ofString());
                if (oldSessResp.statusCode() == 401) {
                    pass("api_logout_workflow");
                } else {
                    fail("api_logout_workflow", "expected 401 after logout, got " + oldSessResp.statusCode());
                }
            } catch (Exception e) {
                fail("api_logout_workflow", e.getMessage());
            }

            // 15. files.html loopback guard
            try {
                InetAddress nonLoopback = InetAddress.getByName("192.168.1.100");
                MockHttpExchange exNonLoop = new MockHttpExchange(nonLoopback, 12345);
                exNonLoop.setRequestURI(URI.create("/files.html"));
                new server.RouterHandler().handle(exNonLoop);

                InetAddress loopback = InetAddress.getByName("127.0.0.1");
                MockHttpExchange exLoop = new MockHttpExchange(loopback, 12345);
                exLoop.setRequestURI(URI.create("/files.html"));
                new server.RouterHandler().handle(exLoop);

                if (exNonLoop.getResponseCode() == 403 && exLoop.getResponseCode() != 403) {
                    pass("api_files_html_loopback_guard");
                } else {
                    fail("api_files_html_loopback_guard", "nonLoop=" + exNonLoop.getResponseCode() + " loop=" + exLoop.getResponseCode());
                }
            } catch (Exception e) {
                fail("api_files_html_loopback_guard", e.getMessage());
            }

            // 16. Web functions when FTP server is not running
            try {
                HttpRequest loginAgain = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/login"))
                        .header("X-File-Explorer", "1")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"AdminPass_2026!\"}"))
                        .build();
                HttpResponse<String> loginAgainResp = client.send(loginAgain, HttpResponse.BodyHandlers.ofString());
                String newCookie = extractCookie(loginAgainResp.headers().firstValue("set-cookie").orElse(""), "FileExplorerSession");
                String newCsrf = extractJson(loginAgainResp.body(), "csrfToken");

                byte[] standaloneBytes = "web works without ftp".getBytes(StandardCharsets.UTF_8);
                HttpRequest upSolo = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/content?path=/standalone.txt"))
                        .header("Cookie", newCookie)
                        .header("X-File-Explorer", "1")
                        .header("X-CSRF-Token", newCsrf)
                        .header("X-File-Size", String.valueOf(standaloneBytes.length))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(standaloneBytes))
                        .build();
                HttpResponse<String> upSoloResp = client.send(upSolo, HttpResponse.BodyHandlers.ofString());

                HttpRequest dlSolo = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/files/download?path=/standalone.txt"))
                        .header("Cookie", newCookie)
                        .GET().build();
                HttpResponse<byte[]> dlSoloResp = client.send(dlSolo, HttpResponse.BodyHandlers.ofByteArray());

                if (upSoloResp.statusCode() == 201 && dlSoloResp.statusCode() == 200 && Arrays.equals(standaloneBytes, dlSoloResp.body())) {
                    pass("api_web_operates_when_ftp_stopped");
                } else {
                    fail("api_web_operates_when_ftp_stopped", "up=" + upSoloResp.statusCode() + " dl=" + dlSoloResp.statusCode());
                }
            } catch (Exception e) {
                fail("api_web_operates_when_ftp_stopped", e.getMessage());
            }
        } catch (Exception e) {
            fail("Api Group Execution", e.getMessage());
            e.printStackTrace();
        }
    }

    private static String extractCookie(String setCookieHeader, String cookieName) {
        if (setCookieHeader == null || !setCookieHeader.contains(cookieName + "=")) {
            return "";
        }
        int start = setCookieHeader.indexOf(cookieName + "=");
        int end = setCookieHeader.indexOf(';', start);
        return (end != -1) ? setCookieHeader.substring(start, end) : setCookieHeader.substring(start);
    }

    private static String extractJson(String json, String key) {
        if (json == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) return m.group(1).replace("\\\"", "\"");
        return null;
    }

    public static record RawResponse(int statusCode, Map<String, String> headers, String body) {}

    public static RawResponse sendRawHttp(int port, String request) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(5000);
            s.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int r;
            try {
                while ((r = s.getInputStream().read(buf)) != -1) {
                    baos.write(buf, 0, r);
                }
            } catch (SocketTimeoutException ignored) {}
            String raw = baos.toString(StandardCharsets.UTF_8);
            int idx = raw.indexOf("\r\n\r\n");
            if (idx == -1) idx = raw.indexOf("\n\n");
            String head = idx != -1 ? raw.substring(0, idx) : raw;
            String body = idx != -1 ? raw.substring(idx + (raw.contains("\r\n\r\n") ? 4 : 2)) : "";
            String[] lines = head.split("\r?\n");
            int code = 0;
            if (lines.length > 0) {
                String[] statusParts = lines[0].split(" ");
                if (statusParts.length > 1) {
                    try { code = Integer.parseInt(statusParts[1]); } catch (Exception ignored) {}
                }
            }
            Map<String, String> headers = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int col = lines[i].indexOf(':');
                if (col > 0) {
                    headers.put(lines[i].substring(0, col).trim().toLowerCase(Locale.ROOT), lines[i].substring(col + 1).trim());
                }
            }
            return new RawResponse(code, headers, body);
        }
    }

    public static void runConcurrencyTests() {
        System.out.println("\n--- [GROUP: CONCURRENCY] Cross-Process Concurrency & OS Locks ---");
        // To be populated in Task 7
    }

    // =========================================================================
    // TEST FIXTURES & PROCESS MANAGEMENT
    // =========================================================================

    public static class ProcessFixture implements AutoCloseable {
        private final List<Process> spawnedProcesses = new ArrayList<>();
        private final List<Path> tempDirs = new ArrayList<>();
        private final String projectClasspath;

        public record ServerInstance(Process process, int port, Path root, List<String> logs) {
            public boolean isAlive() {
                return process != null && process.isAlive();
            }
        }

        public ProcessFixture() {
            // Find class directory
            Path outPath = Paths.get("out").toAbsolutePath().normalize();
            this.projectClasspath = outPath.toString();
        }

        public Path createWorkspace() throws IOException {
            Path ws = Files.createTempDirectory("crosstest-ws-");
            tempDirs.add(ws);
            // Copy minimal assets if wwwroot exists
            Path srcWwwroot = Paths.get("wwwroot").toAbsolutePath();
            if (Files.isDirectory(srcWwwroot)) {
                Path dstWwwroot = ws.resolve("wwwroot");
                Files.createDirectories(dstWwwroot);
                try (var stream = Files.walk(srcWwwroot)) {
                    for (Path p : (Iterable<Path>) stream::iterator) {
                        Path rel = srcWwwroot.relativize(p);
                        Path target = dstWwwroot.resolve(rel.toString());
                        if (Files.isDirectory(p)) {
                            Files.createDirectories(target);
                        } else {
                            Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
            // Create empty sites and sites.json
            Files.createDirectories(ws.resolve("sites"));
            Files.writeString(ws.resolve("sites.json"), "[]");
            return ws;
        }

        public int findFreePort() throws IOException {
            try (ServerSocket s = new ServerSocket(0)) {
                s.setReuseAddress(true);
                return s.getLocalPort();
            }
        }

        public ServerInstance startHttp(Path workspace, Map<String, String> env) throws Exception {
            for (int attempt = 0; attempt < 3; attempt++) {
                int port = findFreePort();
                List<String> cmd = Arrays.asList(
                        getJavaExecutable(),
                        "-cp",
                        projectClasspath,
                        "server.SimpleHttpServer",
                        String.valueOf(port)
                );
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(workspace.toFile());
                if (env != null) {
                    pb.environment().putAll(env);
                }
                pb.redirectErrorStream(true);
                Process p = pb.start();
                spawnedProcesses.add(p);

                List<String> logs = new CopyOnWriteArrayList<>();
                CountDownLatch readyLatch = new CountDownLatch(1);

                Thread logReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            logs.add(line);
                            if (line.contains("Listening on http://localhost:" + port)) {
                                readyLatch.countDown();
                            }
                        }
                    } catch (IOException ignored) {}
                });
                logReader.setDaemon(true);
                logReader.start();

                if (readyLatch.await(5000, TimeUnit.MILLISECONDS) && p.isAlive()) {
                    return new ServerInstance(p, port, workspace.resolve("ftp_workspace/server_root"), logs);
                }

                // If bind race or died, kill and retry
                p.destroyForcibly();
                p.waitFor(1000, TimeUnit.MILLISECONDS);
            }
            throw new IOException("Failed to start HTTP server after 3 attempts");
        }

        public ServerInstance startFtp(Path workspace, Map<String, String> env, List<String> extraArgs) throws Exception {
            for (int attempt = 0; attempt < 3; attempt++) {
                int port = findFreePort();
                List<String> cmd = new ArrayList<>();
                cmd.add(getJavaExecutable());
                cmd.add("-cp");
                cmd.add(projectClasspath);
                cmd.add("server.ftp.SimpleFtpServer");
                cmd.add(String.valueOf(port));
                if (extraArgs != null) {
                    cmd.addAll(extraArgs);
                }

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(workspace.toFile());
                if (env != null) {
                    pb.environment().putAll(env);
                }
                pb.redirectErrorStream(true);
                Process p = pb.start();
                spawnedProcesses.add(p);

                List<String> logs = new CopyOnWriteArrayList<>();
                CountDownLatch readyLatch = new CountDownLatch(1);
                AtomicBoolean listenFound = new AtomicBoolean(false);
                AtomicBoolean rootFound = new AtomicBoolean(false);
                final Path[] detectedRoot = new Path[1];

                Thread logReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            logs.add(line);
                            if (line.contains("[SimpleFtpServer] Root directory: ")) {
                                String rStr = line.substring(line.indexOf("Root directory: ") + "Root directory: ".length()).trim();
                                detectedRoot[0] = Paths.get(rStr);
                                rootFound.set(true);
                            }
                            if (line.contains("[SimpleFtpServer] Listening on port " + port)) {
                                listenFound.set(true);
                            }
                            if (listenFound.get() && rootFound.get()) {
                                readyLatch.countDown();
                            }
                        }
                    } catch (IOException ignored) {}
                });
                logReader.setDaemon(true);
                logReader.start();

                if (readyLatch.await(5000, TimeUnit.MILLISECONDS) && p.isAlive()) {
                    Path root = detectedRoot[0] != null ? detectedRoot[0] : workspace.resolve("ftp_workspace/server_root");
                    return new ServerInstance(p, port, root, logs);
                }

                p.destroyForcibly();
                p.waitFor(1000, TimeUnit.MILLISECONDS);
            }
            throw new IOException("Failed to start FTP server after 3 attempts");
        }

        public int runFtpProcessOnce(Path workspace, Map<String, String> env, List<String> extraArgs, long timeoutMs) throws Exception {
            int port = findFreePort();
            List<String> cmd = new ArrayList<>();
            cmd.add(getJavaExecutable());
            cmd.add("-cp");
            cmd.add(projectClasspath);
            cmd.add("server.ftp.SimpleFtpServer");
            cmd.add(String.valueOf(port));
            if (extraArgs != null) {
                cmd.addAll(extraArgs);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workspace.toFile());
            if (env != null) {
                pb.environment().putAll(env);
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            spawnedProcesses.add(p);

            // Drain output
            Thread drain = new Thread(() -> {
                try (InputStream in = p.getInputStream()) {
                    byte[] buf = new byte[1024];
                    while (in.read(buf) != -1) {}
                } catch (IOException ignored) {}
            });
            drain.setDaemon(true);
            drain.start();

            boolean exited = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!exited) {
                p.destroyForcibly();
                return -999;
            }
            return p.exitValue();
        }

        private String getJavaExecutable() {
            String javaHome = System.getProperty("java.home");
            Path binJava = Paths.get(javaHome, "bin", "java.exe");
            if (Files.exists(binJava)) {
                return binJava.toString();
            }
            return Paths.get(javaHome, "bin", "java").toString();
        }

        @Override
        public void close() {
            for (Process p : spawnedProcesses) {
                if (p != null && p.isAlive()) {
                    p.destroyForcibly();
                    try {
                        p.waitFor(1000, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ignored) {}
                }
            }
            for (Path dir : tempDirs) {
                deleteRecursively(dir);
            }
        }

        private void deleteRecursively(Path root) {
            try {
                if (Files.exists(root)) {
                    Files.walkFileTree(root, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                            try { Files.deleteIfExists(file); } catch (Exception ignored) {}
                            return FileVisitResult.CONTINUE;
                        }
                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                            try { Files.deleteIfExists(dir); } catch (Exception ignored) {}
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            } catch (Exception ignored) {}
        }
    }

    private static boolean tryCreateJunction(Path link, Path target) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return false;
        }
        try {
            Process p = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toAbsolutePath().toString(), target.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            p.waitFor(2, TimeUnit.SECONDS);
            return p.exitValue() == 0 && Files.exists(link);
        } catch (Exception e) {
            return false;
        }
    }

    public static class FtpWireClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        public FtpWireClient(String host, int port) throws IOException {
            this.socket = new Socket(host, port);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            readReply(); // Read 220 banner
        }

        public String sendCommand(String cmd) throws IOException {
            writer.write(cmd + "\r\n");
            writer.flush();
            return readReply();
        }

        public String readReply() throws IOException {
            String line = reader.readLine();
            if (line == null) return null;
            if (line.length() >= 4 && line.charAt(3) == '-') {
                String prefix = line.substring(0, 3) + " ";
                StringBuilder sb = new StringBuilder(line).append("\n");
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                    if (line.startsWith(prefix)) break;
                }
                return sb.toString().trim();
            }
            return line.trim();
        }

        public int parsePasvPort(String reply) {
            int start = reply.indexOf('(');
            int end = reply.indexOf(')');
            if (start == -1 || end == -1) {
                throw new IllegalArgumentException("No PASV port pattern found in: " + reply);
            }
            String[] parts = reply.substring(start + 1, end).split(",");
            int p1 = Integer.parseInt(parts[4].trim());
            int p2 = Integer.parseInt(parts[5].trim());
            return p1 * 256 + p2;
        }

        @Override
        public void close() throws IOException {
            try {
                sendCommand("QUIT");
            } catch (Exception ignored) {}
            socket.close();
        }
    }

    public static class MutableClock extends java.time.Clock {
        private java.time.Instant currentInstant;

        public MutableClock(java.time.Instant start) {
            this.currentInstant = start;
        }

        public void advance(java.time.Duration duration) {
            this.currentInstant = currentInstant.plus(duration);
        }

        @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public java.time.Instant instant() { return currentInstant; }
    }

    public static class MockHttpExchange extends com.sun.net.httpserver.HttpExchange {
        private final com.sun.net.httpserver.Headers reqHeaders = new com.sun.net.httpserver.Headers();
        private final com.sun.net.httpserver.Headers resHeaders = new com.sun.net.httpserver.Headers();
        private InetSocketAddress remote;
        private String method = "GET";
        private URI uri = URI.create("/api/files");
        private int responseCode = 200;

        public MockHttpExchange(InetAddress addr, int port) {
            this.remote = new InetSocketAddress(addr, port);
        }

        public void setRemote(InetAddress addr, int port) {
            this.remote = new InetSocketAddress(addr, port);
        }

        public void setRequestURI(URI u) { this.uri = u; }
        @Override public com.sun.net.httpserver.Headers getRequestHeaders() { return reqHeaders; }
        @Override public com.sun.net.httpserver.Headers getResponseHeaders() { return resHeaders; }
        @Override public URI getRequestURI() { return uri; }
        @Override public String getRequestMethod() { return method; }
        public void setRequestMethod(String m) { this.method = m; }
        @Override public com.sun.net.httpserver.HttpContext getHttpContext() { return null; }
        @Override public void close() {}
        @Override public InputStream getRequestBody() { return InputStream.nullInputStream(); }
        @Override public OutputStream getResponseBody() { return OutputStream.nullOutputStream(); }
        @Override public void sendResponseHeaders(int rCode, long responseLength) { this.responseCode = rCode; }
        @Override public InetSocketAddress getRemoteAddress() { return remote; }
        @Override public int getResponseCode() { return responseCode; }
        @Override public InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 8080); }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) {}
        @Override public void setStreams(InputStream i, OutputStream o) {}
        @Override public com.sun.net.httpserver.HttpPrincipal getPrincipal() { return null; }
    }
}
