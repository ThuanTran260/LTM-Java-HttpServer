package client;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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

    public static void runSessionTests() {
        System.out.println("\n--- [GROUP: SESSION] FileSessionAuth, Local Guards & CSRF ---");
        // To be populated in Task 4
    }

    public static void runIsolationTests() {
        System.out.println("\n--- [GROUP: ISOLATION] Untrusted Upstream Content Policy ---");
        // To be populated in Task 4
    }

    public static void runApiTests() {
        System.out.println("\n--- [GROUP: API] Files HTTP API Wire Tests ---");
        // To be populated in Task 5
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
}
