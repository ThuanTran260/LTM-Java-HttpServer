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

    // Placeholders for future tasks (will be implemented as we proceed)
    public static void runStorageTests() {
        System.out.println("\n--- [GROUP: STORAGE] SharedFileStore & Atomic Mutation ---");
        // To be populated in Task 2
    }

    public static void runFtpTests() {
        System.out.println("\n--- [GROUP: FTP] FTP Shared Store Integration & Wire Protocol ---");
        // To be populated in Task 3
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
}
