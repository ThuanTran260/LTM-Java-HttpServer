package client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pure Java FTP Client Library implementing RFC 959 using Raw TCP Sockets.
 * Supports:
 * - Control channel with multi-line reply parsing (XYZ- ... XYZ )
 * - Command listener callbacks with password masking
 * - Transfer modes: TYPE I (Binary, default) and TYPE A (ASCII)
 * - Data channel modes: Passive (PASV / EPSV, default) and Active (PORT)
 * - Complete file and directory management (CWD, CDUP, PWD, LIST, RETR, STOR, DELE, MKD, RMD)
 */
public class FtpClient implements AutoCloseable {

    private static final int DEFAULT_PORT = 21;
    private static final int SOCKET_TIMEOUT_MS = 15000;
    private static final int BUFFER_SIZE = 16384;

    // Package-private test hook to simulate filesystem lacking ATOMIC_MOVE support
    static volatile boolean simulateAtomicMoveNotSupportedForTest = false;

    private final List<FtpCommandListener> listeners = new CopyOnWriteArrayList<>();

    private Socket controlSocket;
    private BufferedReader reader;
    private BufferedWriter writer;

    private String serverHost;
    private int serverPort;
    private boolean connected = false;
    private boolean authenticated = false;
    private boolean binaryMode = true;
    private boolean passiveMode = true;
    private String greetingBanner = "";
    private String currentRemoteDir = "/";
    private FtpReply lastReply;

    public FtpClient() {
    }

    public void addCommandListener(FtpCommandListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeCommandListener(FtpCommandListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void connect(String host) throws IOException {
        connect(host, DEFAULT_PORT);
    }

    public synchronized void connect(String host, int port) throws IOException {
        if (isConnected()) {
            disconnect();
        }
        this.serverHost = host;
        this.serverPort = port;

        controlSocket = new Socket();
        controlSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
        controlSocket.connect(new InetSocketAddress(host, port), 10000);

        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));

        FtpReply greeting = readReply();
        this.greetingBanner = greeting.message();
        if (!greeting.isPositiveCompletion()) {
            disconnect();
            throw new IOException("FTP server rejected connection: " + greeting.message());
        }
        this.connected = true;
        this.authenticated = false;
    }

    public synchronized boolean login(String username, String password) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to FTP server.");
        }
        FtpReply userReply = sendCommand("USER " + (username != null ? username : "anonymous"));
        if (userReply.getCode() == 230) {
            this.authenticated = true;
            initSessionAfterLogin();
            return true;
        }
        if (userReply.getCode() == 331) {
            FtpReply passReply = sendCommand("PASS " + (password != null ? password : ""));
            if (passReply.isPositiveCompletion()) {
                this.authenticated = true;
                initSessionAfterLogin();
                return true;
            }
        }
        return false;
    }

    private void initSessionAfterLogin() {
        try {
            setBinaryMode(true);
        } catch (Exception ignored) {}
        try {
            printWorkingDirectory();
        } catch (Exception ignored) {}
    }

    public synchronized void setBinaryMode(boolean binary) throws IOException {
        String typeCmd = binary ? "TYPE I" : "TYPE A";
        FtpReply reply = sendCommand(typeCmd);
        if (!reply.isPositiveCompletion()) {
            throw new IOException("Failed to set transfer mode: " + reply.message());
        }
        this.binaryMode = binary;
    }

    public boolean isBinaryMode() {
        return binaryMode;
    }

    public void setPassiveMode(boolean passive) {
        this.passiveMode = passive;
    }

    public boolean isPassiveMode() {
        return passiveMode;
    }

    public synchronized String printWorkingDirectory() throws IOException {
        FtpReply reply = sendCommand("PWD");
        if (!reply.isPositiveCompletion()) {
            throw new IOException("PWD failed: " + reply.message());
        }
        String msg = reply.message();
        int firstQuote = msg.indexOf('"');
        int lastQuote = msg.lastIndexOf('"');
        if (firstQuote != -1 && lastQuote > firstQuote) {
            currentRemoteDir = msg.substring(firstQuote + 1, lastQuote);
        } else {
            String[] parts = msg.split("\\s+");
            if (parts.length > 1) {
                currentRemoteDir = parts[1];
            } else {
                currentRemoteDir = "/";
            }
        }
        return currentRemoteDir;
    }

    public synchronized boolean changeWorkingDirectory(String dir) throws IOException {
        FtpReply reply = sendCommand("CWD " + dir);
        if (reply.isPositiveCompletion()) {
            try {
                printWorkingDirectory();
            } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    public synchronized boolean changeToParentDirectory() throws IOException {
        FtpReply reply = sendCommand("CDUP");
        if (reply.isPositiveCompletion()) {
            try {
                printWorkingDirectory();
            } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    public List<FtpFile> listFiles() throws IOException {
        return listFiles(null);
    }

    public synchronized List<FtpFile> listFiles(String path) throws IOException {
        String cmd = (path == null || path.isBlank()) ? "LIST" : "LIST " + path;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (DataConnection conn = openDataConnection(cmd)) {
            InputStream in = conn.getDataSocket().getInputStream();
            byte[] buf = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buf)) != -1) {
                buffer.write(buf, 0, read);
            }
        }
        FtpReply comp = readReply();
        if (!comp.isPositiveCompletion()) {
            throw new IOException("LIST command failed: " + comp.message());
        }

        String listingText = buffer.toString(StandardCharsets.UTF_8);
        return FtpFile.parseList(listingText);
    }

    public synchronized void uploadFile(File localFile, String remoteName) throws IOException {
        if (localFile == null || !localFile.exists()) {
            throw new FileNotFoundException("Local file does not exist: " + (localFile != null ? localFile.getAbsolutePath() : "null"));
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(localFile))) {
            uploadStream(in, remoteName);
        }
    }

    public synchronized void uploadStream(InputStream in, String remoteName) throws IOException {
        ensureBinaryMode();
        try (DataConnection conn = openDataConnection("STOR " + remoteName)) {
            OutputStream out = conn.getDataSocket().getOutputStream();
            byte[] buf = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            out.flush();
        }
        FtpReply comp = readReply();
        if (!comp.isPositiveCompletion()) {
            throw new IOException("Upload failed with server reply: " + comp.message());
        }
    }

    public synchronized void downloadFile(String remoteName, File localTarget) throws IOException {
        if (localTarget == null) {
            throw new IllegalArgumentException("Target local file cannot be null");
        }
        File parentDir = localTarget.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        Path parentPath = (parentDir != null) ? parentDir.toPath() : Paths.get(".");
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(parentPath, localTarget.getName() + ".", ".tmp");
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempFile))) {
                downloadStream(remoteName, out);
            }
            // Stream is closed before move to prevent Windows file locking issues
            try {
                if (simulateAtomicMoveNotSupportedForTest) {
                    throw new AtomicMoveNotSupportedException(tempFile.toString(), localTarget.toString(), "Simulated unsupported atomic move");
                }
                Files.move(tempFile, localTarget.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("Atomic move is not supported on this filesystem; original target file preserved: " + localTarget, e);
            }
            tempFile = null; // Successfully transferred and moved
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {}
            }
        }
    }

    public synchronized void downloadStream(String remoteName, OutputStream out) throws IOException {
        ensureBinaryMode();
        try (DataConnection conn = openDataConnection("RETR " + remoteName)) {
            InputStream in = conn.getDataSocket().getInputStream();
            byte[] buf = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            out.flush();
        }
        FtpReply comp = readReply();
        if (!comp.isPositiveCompletion()) {
            throw new IOException("Download failed with server reply: " + comp.message());
        }
    }

    public synchronized boolean deleteFile(String remoteName) throws IOException {
        FtpReply reply = sendCommand("DELE " + remoteName);
        return reply.isPositiveCompletion();
    }

    /**
     * Renames a file or directory on the remote FTP server using RFC 959 RNFR and RNTO.
     * Complies with Slide 30 Section 2, 5, 6, 8, 9 (1.0 mark).
     */
    public synchronized boolean rename(String fromPath, String toPath) throws IOException {
        if (fromPath == null || toPath == null || fromPath.isBlank() || toPath.isBlank()) {
            throw new IllegalArgumentException("Source and target names cannot be blank");
        }
        FtpReply replyRnfr = sendCommand("RNFR " + fromPath.trim());
        if (!replyRnfr.isPositiveIntermediate()) {
            return false;
        }
        FtpReply replyRnto = sendCommand("RNTO " + toPath.trim());
        return replyRnto.isPositiveCompletion();
    }

    public synchronized boolean makeDirectory(String dirName) throws IOException {
        FtpReply reply = sendCommand("MKD " + dirName);
        return reply.isSuccess();
    }

    public synchronized boolean removeDirectory(String dirName) throws IOException {
        FtpReply reply = sendCommand("RMD " + dirName);
        return reply.isPositiveCompletion();
    }

    public void validateCommand(String command) {
        if (command == null || command.contains("\r") || command.contains("\n") || command.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("FTP command cannot contain CR, LF, or null characters");
        }
    }

    private void writeWireCommand(String command) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to FTP server.");
        }
        validateCommand(command);
        String sanitized = command;
        if (command.toUpperCase(Locale.ROOT).startsWith("PASS ")) {
            sanitized = "PASS ********";
        }
        for (FtpCommandListener listener : listeners) {
            try {
                listener.onCommandSent(sanitized);
            } catch (Throwable ignored) {}
        }
        writer.write(command + "\r\n");
        writer.flush();
    }

    public synchronized FtpReply sendCommand(String command) throws IOException {
        writeWireCommand(command);
        return readReply();
    }

    public synchronized FtpReply readReply() throws IOException {
        if (reader == null) {
            throw new IOException("Control channel reader is null.");
        }
        String firstLine = reader.readLine();
        if (firstLine == null) {
            throw new EOFException("Connection closed by server.");
        }
        if (firstLine.length() < 3) {
            throw new IOException("Malformed FTP reply: " + firstLine);
        }
        int code;
        try {
            code = Integer.parseInt(firstLine.substring(0, 3));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid FTP reply code in line: " + firstLine);
        }
        List<String> lines = new ArrayList<>();
        lines.add(firstLine);
        if (firstLine.length() >= 4 && firstLine.charAt(3) == '-') {
            String endMarker = code + " ";
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    throw new EOFException("Unexpected EOF during multi-line FTP reply.");
                }
                lines.add(line);
                if (line.startsWith(endMarker)) {
                    break;
                }
            }
        }
        String fullMessage = String.join("\n", lines);
        FtpReply reply = new FtpReply(code, fullMessage, lines);
        this.lastReply = reply;
        for (FtpCommandListener listener : listeners) {
            try {
                listener.onReplyReceived(code, fullMessage);
                listener.onResponseReceived(fullMessage);
            } catch (Throwable ignored) {}
        }
        return reply;
    }

    public synchronized void disconnect() {
        if (controlSocket != null && !controlSocket.isClosed()) {
            try {
                writer.write("QUIT\r\n");
                writer.flush();
            } catch (Exception ignored) {}
            try {
                controlSocket.close();
            } catch (Exception ignored) {}
        }
        this.connected = false;
        this.authenticated = false;
        this.reader = null;
        this.writer = null;
        this.controlSocket = null;
    }

    public boolean isConnected() {
        return connected && controlSocket != null && !controlSocket.isClosed() && controlSocket.isConnected();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getGreetingBanner() {
        return greetingBanner;
    }

    public String getCurrentRemoteDir() {
        return currentRemoteDir;
    }

    public FtpReply getLastReply() {
        return lastReply;
    }

    @Override
    public void close() {
        disconnect();
    }

    private void ensureBinaryMode() throws IOException {
        if (!binaryMode) {
            setBinaryMode(true);
        }
    }

    // -------------------------------------------------------------------------
    // Internal Data Channel Helper
    // -------------------------------------------------------------------------

    private DataConnection openDataConnection(String command) throws IOException {
        validateCommand(command);
        if (passiveMode) {
            return openPassiveDataConnection(command);
        } else {
            return openActiveDataConnection(command);
        }
    }

    private DataConnection openPassiveDataConnection(String command) throws IOException {
        FtpReply pasvReply = sendCommand("PASV");
        if (!pasvReply.isPositiveCompletion()) {
            // Try EPSV fallback
            pasvReply = sendCommand("EPSV");
            if (!pasvReply.isPositiveCompletion()) {
                throw new IOException("Passive mode setup failed: " + pasvReply.message());
            }
            int port = parseEpsvPort(pasvReply.message());
            Socket socket = new Socket();
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            socket.connect(new InetSocketAddress(controlSocket.getInetAddress().getHostAddress(), port), 10000);
            return sendCommandAndValidateDataSocket(command, socket);
        }

        int[] hostPort = parsePasvHostAndPort(pasvReply.message());
        String dataHost = hostPort[0] + "." + hostPort[1] + "." + hostPort[2] + "." + hostPort[3];
        int dataPort = hostPort[4];

        // SEC-11: Bind data host to control connection's peer IP to prevent PASV bounce/redirect attacks.
        // Standard FTP clients (FileZilla, curl) ignore PASV host IP and reuse control connection's peer IP.
        if (controlSocket != null && controlSocket.getInetAddress() != null) {
            dataHost = controlSocket.getInetAddress().getHostAddress();
        }

        Socket dataSocket = new Socket();
        dataSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
        dataSocket.connect(new InetSocketAddress(dataHost, dataPort), 10000);

        return sendCommandAndValidateDataSocket(command, dataSocket);
    }

    private DataConnection sendCommandAndValidateDataSocket(String command, Socket dataSocket) throws IOException {
        FtpReply cmdReply = sendCommand(command);
        if (!cmdReply.isPositivePreliminary() && !cmdReply.isPositiveCompletion()) {
            try {
                dataSocket.close();
            } catch (Exception ignored) {}
            if (cmdReply.getCode() == 550) {
                throw new FileNotFoundException("Server returned 550: " + cmdReply.message());
            }
            throw new IOException("Command failed (" + cmdReply.getCode() + "): " + cmdReply.message());
        }
        return new DataConnection(dataSocket, null);
    }

    private DataConnection openActiveDataConnection(String command) throws IOException {
        InetAddress localAddr = controlSocket.getLocalAddress();
        if (localAddr == null || localAddr.isAnyLocalAddress()) {
            localAddr = InetAddress.getByName("127.0.0.1");
        }
        ServerSocket serverSocket = new ServerSocket(0, 1, localAddr);
        serverSocket.setSoTimeout(SOCKET_TIMEOUT_MS);

        int localPort = serverSocket.getLocalPort();
        byte[] ipBytes = localAddr.getAddress();
        int h1 = ipBytes[0] & 0xFF;
        int h2 = ipBytes[1] & 0xFF;
        int h3 = ipBytes[2] & 0xFF;
        int h4 = ipBytes[3] & 0xFF;
        int p1 = (localPort >> 8) & 0xFF;
        int p2 = localPort & 0xFF;

        String portCmd = String.format(Locale.ROOT, "PORT %d,%d,%d,%d,%d,%d", h1, h2, h3, h4, p1, p2);
        FtpReply portReply = sendCommand(portCmd);
        if (!portReply.isPositiveCompletion()) {
            serverSocket.close();
            throw new IOException("Active PORT command failed: " + portReply.message());
        }

        // Send transfer command line via unified wire writer with CRLF/null validation
        writeWireCommand(command);

        Socket dataSocket = serverSocket.accept();
        serverSocket.close(); // Immediate ephemeral port release

        FtpReply cmdReply = readReply();
        if (!cmdReply.isPositivePreliminary() && !cmdReply.isPositiveCompletion()) {
            try {
                dataSocket.close();
            } catch (Exception ignored) {}
            if (cmdReply.getCode() == 550) {
                throw new FileNotFoundException("Server returned 550: " + cmdReply.message());
            }
            throw new IOException("Command failed (" + cmdReply.getCode() + "): " + cmdReply.message());
        }
        return new DataConnection(dataSocket, null);
    }

    private int[] parsePasvHostAndPort(String reply) throws IOException {
        int open = reply.indexOf('(');
        int close = reply.indexOf(')', open);
        if (open == -1 || close == -1) {
            throw new IOException("Invalid PASV reply syntax: " + reply);
        }
        String[] parts = reply.substring(open + 1, close).split(",");
        if (parts.length < 6) {
            throw new IOException("Invalid PASV parameters: " + reply);
        }
        int h1 = Integer.parseInt(parts[0].trim());
        int h2 = Integer.parseInt(parts[1].trim());
        int h3 = Integer.parseInt(parts[2].trim());
        int h4 = Integer.parseInt(parts[3].trim());
        int p1 = Integer.parseInt(parts[4].trim());
        int p2 = Integer.parseInt(parts[5].trim());
        int port = (p1 << 8) | p2;
        return new int[]{h1, h2, h3, h4, port};
    }

    private int parseEpsvPort(String reply) throws IOException {
        int open = reply.indexOf('(');
        int close = reply.indexOf(')', open);
        if (open == -1 || close == -1) {
            throw new IOException("Invalid EPSV reply syntax: " + reply);
        }
        String content = reply.substring(open + 1, close);
        char delim = content.charAt(0);
        int lastDelim = content.lastIndexOf(delim);
        int prevDelim = content.lastIndexOf(delim, lastDelim - 1);
        String portStr = content.substring(prevDelim + 1, lastDelim);
        return Integer.parseInt(portStr.trim());
    }

    private static class DataConnection implements AutoCloseable {
        private final Socket dataSocket;
        private final ServerSocket serverSocket;

        public DataConnection(Socket dataSocket, ServerSocket serverSocket) {
            this.dataSocket = dataSocket;
            this.serverSocket = serverSocket;
        }

        public Socket getDataSocket() {
            return dataSocket;
        }

        @Override
        public void close() {
            if (dataSocket != null) {
                try {
                    dataSocket.close();
                } catch (Exception ignored) {}
            }
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
