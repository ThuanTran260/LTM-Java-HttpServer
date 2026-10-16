package server.ftp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pure Java FTP Server implementation (RFC 959).
 * Uses Raw TCP Sockets and JDK 24 Virtual Threads.
 * Listens on port 2121 by default.
 */
public class SimpleFtpServer implements AutoCloseable {

    public static final int DEFAULT_PORT = 2121;
    public static final String DEFAULT_ROOT = "ftp_workspace/server_root";

    private final int port;
    private final Path serverRoot;
    private ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SimpleFtpServer() {
        this(DEFAULT_PORT, Paths.get(DEFAULT_ROOT));
    }

    public SimpleFtpServer(int port, Path serverRoot) {
        this.port = port;
        this.serverRoot = serverRoot.toAbsolutePath().normalize();
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Files.createDirectories(serverRoot);
        serverSocket = new ServerSocket(port);
        System.out.println("[SimpleFtpServer] Listening on port " + port + " (JDK 24 Virtual Threads)");
        System.out.println("[SimpleFtpServer] Root directory: " + serverRoot);

        Thread.ofVirtual().name("ftp-server-accept").start(() -> {
            while (running.get() && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    Thread.ofVirtual()
                          .name("ftp-session-" + clientSocket.getRemoteSocketAddress())
                          .start(new FtpSessionHandler(clientSocket, serverRoot));
                } catch (IOException e) {
                    if (!running.get() || serverSocket.isClosed()) {
                        break;
                    }
                    System.err.println("[SimpleFtpServer] Accept error: " + e.getMessage());
                }
            }
        });
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException ignored) {}
            System.out.println("[SimpleFtpServer] Server stopped.");
        }
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        return running.get() && serverSocket != null && !serverSocket.isClosed();
    }

    public int getPort() {
        return port;
    }

    public Path getServerRoot() {
        return serverRoot;
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.exit(1);
            }
        }
        server.FileStoreConfig config;
        try {
            config = server.FileStoreConfig.fromEnvironment();
        } catch (Exception e) {
            System.err.println("[SimpleFtpServer] Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }
        Path root = config.root();
        if (args.length > 1) {
            root = Paths.get(args[1]);
        }

        try {
            SimpleFtpServer server = new SimpleFtpServer(port, root);
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[SimpleFtpServer] Shutting down via hook...");
                server.stop();
            }));

            // Keep process alive while server is running
            while (server.isRunning()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[SimpleFtpServer] Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
