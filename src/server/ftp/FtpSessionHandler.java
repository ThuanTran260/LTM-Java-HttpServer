package server.ftp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Handles an individual FTP client session on a dedicated JDK 24 Virtual Thread.
 * Implements RFC 959 state machine, pre-auth enforcement, command dispatching,
 * and data transfer lifecycle.
 */
public class FtpSessionHandler implements Runnable, AutoCloseable {

    private final Socket controlSocket;
    private final Path serverRoot;
    private final FtpPathSandbox sandbox;
    private final FtpDataChannel dataChannel;

    private BufferedReader reader;
    private BufferedWriter writer;

    private boolean authenticated = false;
    private String sessionUser = null;
    private FtpUserAuth.Role role = FtpUserAuth.Role.GUEST;
    private String currentVirtualDir = "/";
    private boolean binaryMode = true;
    private volatile boolean running = true;
    private Path pendingRenameFrom = null;

    public FtpSessionHandler(Socket controlSocket, Path serverRoot) {
        this.controlSocket = controlSocket;
        this.serverRoot = serverRoot.toAbsolutePath().normalize();
        this.sandbox = new FtpPathSandbox(this.serverRoot);
        this.dataChannel = new FtpDataChannel();
    }

    @Override
    public void run() {
        try {
            // Idle control connection timeout: 300s (5 minutes)
            controlSocket.setSoTimeout(300000);
            reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));

            sendReply("220 Simple Java FTP Server ready (JDK 24 Virtual Threads).\r\n");

            while (running && !controlSocket.isClosed()) {
                String line;
                try {
                    line = reader.readLine();
                } catch (SocketTimeoutException ste) {
                    sendReply("421 Timeout: closing control connection.\r\n");
                    break;
                } catch (IOException ioe) {
                    break; // Client closed connection
                }

                if (line == null) {
                    break; // EOF
                }

                line = line.trim();
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1).trim();
                }
                if (line.isEmpty()) {
                    continue;
                }

                handleCommandLine(line);
            }
        } catch (Exception e) {
            // Session terminated
        } finally {
            close();
        }
    }

    private void handleCommandLine(String line) {
        int spaceIdx = line.indexOf(' ');
        String cmd = (spaceIdx == -1 ? line : line.substring(0, spaceIdx)).toUpperCase(Locale.ROOT);
        String arg = (spaceIdx == -1 ? "" : line.substring(spaceIdx + 1).trim());

        // Pre-authentication enforcement
        if (!authenticated) {
            switch (cmd) {
                case "USER":
                case "PASS":
                case "QUIT":
                case "NOOP":
                case "FEAT":
                case "SYST":
                case "AUTH":
                case "OPTS":
                    break; // Allowed before login
                default:
                    sendReply("530 Please login with USER and PASS.\r\n");
                    return;
            }
        }

        switch (cmd) {
            case "USER":
                handleUser(arg);
                break;
            case "PASS":
                handlePass(arg);
                break;
            case "SYST":
                sendReply("215 UNIX Type: L8\r\n");
                break;
            case "FEAT":
                sendReply("211-Features:\r\n UTF8\r\n EPSV\r\n PASV\r\n SIZE\r\n REST STREAM\r\n211 End\r\n");
                break;
            case "OPTS":
                sendReply("200 Always in UTF8 mode.\r\n");
                break;
            case "AUTH":
                sendReply("502 Command not implemented.\r\n");
                break;
            case "NOOP":
                sendReply("200 OK.\r\n");
                break;
            case "QUIT":
                sendReply("221 Goodbye.\r\n");
                running = false;
                break;
            case "PWD":
                sendReply("257 \"" + currentVirtualDir + "\" is current directory.\r\n");
                break;
            case "TYPE":
                handleType(arg);
                break;
            case "PASV":
                handlePasv();
                break;
            case "EPSV":
                handleEpsv();
                break;
            case "PORT":
                handlePort(arg);
                break;
            case "EPRT":
                handleEprt(arg);
                break;
            case "CWD":
                handleCwd(arg);
                break;
            case "CDUP":
                handleCdup();
                break;
            case "LIST":
                handleList(arg);
                break;
            case "NLST":
                handleNlst(arg);
                break;
            case "RETR":
                handleRetr(arg);
                break;
            case "STOR":
                handleStor(arg);
                break;
            case "DELE":
                handleDele(arg);
                break;
            case "MKD":
                handleMkd(arg);
                break;
            case "RMD":
                handleRmd(arg);
                break;
            case "SIZE":
                handleSize(arg);
                break;
            case "MDTM":
                handleMdtm(arg);
                break;
            case "RNFR":
                handleRnfr(arg);
                break;
            case "RNTO":
                handleRnto(arg);
                break;
            case "REST":
                sendReply("350 Restarting at " + arg + ". Send STORE or RETRIEVE to initiate transfer.\r\n");
                break;
            default:
                sendReply("500 Syntax error, command unrecognized: " + cmd + "\r\n");
                break;
        }
    }

    private void handleUser(String username) {
        if (username.isEmpty()) {
            sendReply("501 Syntax error in parameters or arguments.\r\n");
            return;
        }
        sessionUser = username;
        authenticated = false;
        sendReply("331 Password required for " + sessionUser + ".\r\n");
    }

    private void handlePass(String password) {
        if (sessionUser == null || sessionUser.isEmpty()) {
            sendReply("503 Bad sequence of commands.\r\n");
            return;
        }
        if (FtpUserAuth.authenticate(sessionUser, password)) {
            authenticated = true;
            role = FtpUserAuth.getRole(sessionUser);
            currentVirtualDir = "/";
            sendReply("230 User logged in, proceed.\r\n");
        } else {
            authenticated = false;
            sendReply("530 Login incorrect.\r\n");
        }
    }

    private void handleType(String typeCode) {
        if ("I".equalsIgnoreCase(typeCode) || "L8".equalsIgnoreCase(typeCode)) {
            binaryMode = true;
            sendReply("200 Type set to I.\r\n");
        } else if ("A".equalsIgnoreCase(typeCode)) {
            binaryMode = false;
            sendReply("200 Type set to A.\r\n");
        } else {
            binaryMode = true;
            sendReply("200 Type set to I.\r\n");
        }
    }

    private void handlePasv() {
        try {
            InetAddress localAddr = controlSocket.getLocalAddress();
            if (localAddr == null || localAddr.isAnyLocalAddress()) {
                localAddr = InetAddress.getByName("127.0.0.1");
            }
            int port = dataChannel.openPassive(localAddr);
            byte[] ipBytes = localAddr.getAddress();
            int h1 = ipBytes[0] & 0xFF;
            int h2 = ipBytes[1] & 0xFF;
            int h3 = ipBytes[2] & 0xFF;
            int h4 = ipBytes[3] & 0xFF;
            int p1 = port / 256;
            int p2 = port % 256;
            sendReply(String.format(Locale.ROOT, "227 Entering Passive Mode (%d,%d,%d,%d,%d,%d).\r\n", h1, h2, h3, h4, p1, p2));
        } catch (IOException e) {
            sendReply("425 Can't open passive connection.\r\n");
        }
    }

    private void handleEpsv() {
        try {
            InetAddress localAddr = controlSocket.getLocalAddress();
            if (localAddr == null || localAddr.isAnyLocalAddress()) {
                localAddr = InetAddress.getByName("127.0.0.1");
            }
            int port = dataChannel.openPassive(localAddr);
            sendReply(String.format(Locale.ROOT, "229 Entering Extended Passive Mode (|||%d|).\r\n", port));
        } catch (IOException e) {
            sendReply("425 Can't open extended passive connection.\r\n");
        }
    }

    private void handlePort(String params) {
        try {
            String[] parts = params.split(",");
            if (parts.length != 6) {
                sendReply("501 Syntax error in PORT parameters.\r\n");
                return;
            }
            String ip = parts[0].trim() + "." + parts[1].trim() + "." + parts[2].trim() + "." + parts[3].trim();
            int p1 = Integer.parseInt(parts[4].trim());
            int p2 = Integer.parseInt(parts[5].trim());
            int port = p1 * 256 + p2;

            // RFC 2577 Bounce Attack Defense
            InetAddress clientAddr = controlSocket.getInetAddress();
            InetAddress portAddr = InetAddress.getByName(ip);
            boolean match = clientAddr.equals(portAddr);
            if (!match && clientAddr.isLoopbackAddress() && portAddr.isLoopbackAddress()) {
                match = true;
            }
            if (!match) {
                sendReply("500 Illegal PORT command: Host mismatch.\r\n");
                return;
            }

            dataChannel.setActive(ip, port);
            sendReply("200 PORT command successful.\r\n");
        } catch (Exception e) {
            sendReply("501 Syntax error in PORT parameters.\r\n");
        }
    }

    private void handleEprt(String params) {
        try {
            String[] parts = params.split("\\|");
            if (parts.length < 4) {
                sendReply("501 Syntax error in EPRT parameters.\r\n");
                return;
            }
            String ip = parts[2].trim();
            int port = Integer.parseInt(parts[3].trim());

            // RFC 2577 Bounce Attack Defense
            InetAddress clientAddr = controlSocket.getInetAddress();
            InetAddress portAddr = InetAddress.getByName(ip);
            boolean match = clientAddr.equals(portAddr);
            if (!match && clientAddr.isLoopbackAddress() && portAddr.isLoopbackAddress()) {
                match = true;
            }
            if (!match) {
                sendReply("500 Illegal EPRT command: Host mismatch.\r\n");
                return;
            }

            dataChannel.setActive(ip, port);
            sendReply("200 EPRT command successful.\r\n");
        } catch (Exception e) {
            sendReply("501 Syntax error in EPRT parameters.\r\n");
        }
    }

    private void handleCwd(String pathArg) {
        try {
            String targetVirtual = sandbox.resolveVirtual(currentVirtualDir, pathArg, true);
            Path physical = sandbox.resolvePhysical(targetVirtual);
            if (!Files.exists(physical) || !Files.isDirectory(physical)) {
                sendReply("550 Directory not found: " + pathArg + "\r\n");
                return;
            }
            currentVirtualDir = targetVirtual;
            sendReply("250 Directory successfully changed to \"" + currentVirtualDir + "\".\r\n");
        } catch (SecurityException se) {
            sendReply("550 Access denied: " + se.getMessage() + "\r\n");
        } catch (Exception e) {
            sendReply("550 Failed to change directory.\r\n");
        }
    }

    private void handleCdup() {
        try {
            String targetVirtual = sandbox.resolveVirtual(currentVirtualDir, "..", true);
            Path physical = sandbox.resolvePhysical(targetVirtual);
            if (!Files.exists(physical) || !Files.isDirectory(physical)) {
                sendReply("550 Directory not found.\r\n");
                return;
            }
            currentVirtualDir = targetVirtual;
            sendReply("200 Directory successfully changed to \"" + currentVirtualDir + "\".\r\n");
        } catch (Exception e) {
            sendReply("550 Failed to change to parent directory.\r\n");
        }
    }

    private void handleMkd(String dirName) {
        if (!FtpUserAuth.canWrite(role)) {
            sendReply("550 Permission denied: Anonymous user is read-only.\r\n");
            return;
        }
        try {
            String targetVirtual = sandbox.resolveVirtual(currentVirtualDir, dirName, false);
            Path physical = sandbox.resolvePhysical(targetVirtual);
            if (Files.exists(physical)) {
                sendReply("550 Directory already exists.\r\n");
                return;
            }
            Files.createDirectories(physical);
            sendReply("257 \"" + targetVirtual + "\" directory created.\r\n");
        } catch (SecurityException se) {
            sendReply("550 Access denied: " + se.getMessage() + "\r\n");
        } catch (Exception e) {
            sendReply("550 Failed to create directory.\r\n");
        }
    }

    private void handleRmd(String dirName) {
        if (!FtpUserAuth.canDelete(role)) {
            sendReply("550 Permission denied: Anonymous user is read-only.\r\n");
            return;
        }
        try {
            String targetVirtual = sandbox.resolveVirtual(currentVirtualDir, dirName, false);
            Path physical = sandbox.resolvePhysical(targetVirtual);
            if (!Files.exists(physical) || !Files.isDirectory(physical)) {
                sendReply("550 Directory not found: " + dirName + "\r\n");
                return;
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(physical)) {
                if (ds.iterator().hasNext()) {
                    sendReply("550 Directory not empty.\r\n");
                    return;
                }
            }
            Files.delete(physical);
            sendReply("250 Directory removed.\r\n");
        } catch (SecurityException se) {
            sendReply("550 Access denied: " + se.getMessage() + "\r\n");
        } catch (Exception e) {
            sendReply("550 Failed to remove directory.\r\n");
        }
    }

    private void handleDele(String fileName) {
        if (!FtpUserAuth.canDelete(role)) {
            sendReply("550 Permission denied: Anonymous user is read-only.\r\n");
            return;
        }
        try {
            String targetVirtual = sandbox.resolveVirtual(currentVirtualDir, fileName, false);
            Path physical = sandbox.resolvePhysical(targetVirtual);
            if (!Files.exists(physical) || Files.isDirectory(physical)) {
                sendReply("550 File not found: " + fileName + "\r\n");
                return;
            }
            Files.delete(physical);
            sendReply("250 File deleted successfully.\r\n");
        } catch (SecurityException se) {
            sendReply("550 Access denied: " + se.getMessage() + "\r\n");
        } catch (Exception e) {
            sendReply("550 Failed to delete file.\r\n");
        }
    }

    private void handleSize(String fileName) {
        try {
            String targetVirtual = sandbox.resolveVirtual(currentVirtualDir, fileName, false);
            Path physical = sandbox.resolvePhysical(targetVirtual);
            if (!Files.exists(physical) || Files.isDirectory(physical)) {
                sendReply("550 File not found: " + fileName + "\r\n");
                return;
            }
            long size = Files.size(physical);
            sendReply("213 " + size + "\r\n");
        } catch (SecurityException se) {
            sendReply("550 Access denied: " + se.getMessage() + "\r\n");
        } catch (Exception e) {
            sendReply("550 Could not get file size.\r\n");
        }
    }

    private void handleMdtm(String fileName) {
        try {
            String targetVirtual = sandbox.resolveVirtual(currentVirtualDir, fileName, false);
            Path physical = sandbox.resolvePhysical(targetVirtual);
            if (!Files.exists(physical)) {
                sendReply("550 File not found: " + fileName + "\r\n");
                return;
            }
            Instant modTime = Files.getLastModifiedTime(physical).toInstant();
            String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT)
                    .withZone(ZoneOffset.UTC)
                    .format(modTime);
            sendReply("213 " + ts + "\r\n");
        } catch (SecurityException se) {
            sendReply("550 Access denied: " + se.getMessage() + "\r\n");
        } catch (Exception e) {
            sendReply("550 Could not get modification time.\r\n");
        }
    }

    private void handleRnfr(String fileName) {
        if (!FtpUserAuth.canWrite(role)) {
            sendReply("550 Permission denied: Anonymous user is read-only.\r\n");
            return;
        }
        try {
            String targetVirtual = sandbox.resolveVirtual(currentVirtualDir, fileName, false);
            Path physical = sandbox.resolvePhysical(targetVirtual);
            if (!Files.exists(physical)) {
                sendReply("550 File not found: " + fileName + "\r\n");
                return;
            }
            pendingRenameFrom = physical;
            sendReply("350 File exists, ready for destination name.\r\n");
        } catch (SecurityException se) {
            sendReply("550 Access denied: " + se.getMessage() + "\r\n");
        } catch (Exception e) {
            sendReply("550 Could not prepare rename.\r\n");
        }
    }

    private void handleRnto(String fileName) {
        if (!FtpUserAuth.canWrite(role)) {
            sendReply("550 Permission denied: Anonymous user is read-only.\r\n");
            return;
        }
        if (pendingRenameFrom == null) {
            sendReply("503 Bad sequence of commands: RNFR required first.\r\n");
            return;
        }
        try {
            String targetVirtual = sandbox.resolveVirtual(currentVirtualDir, fileName, false);
            Path physicalTarget = sandbox.resolvePhysical(targetVirtual);
            Files.move(pendingRenameFrom, physicalTarget, StandardCopyOption.REPLACE_EXISTING);
            pendingRenameFrom = null;
            sendReply("250 Rename successful.\r\n");
        } catch (SecurityException se) {
            sendReply("550 Access denied: " + se.getMessage() + "\r\n");
        } catch (Exception e) {
            sendReply("550 Rename failed.\r\n");
        }
    }

    private void handleList(String pathArg) {
        Path physicalTarget;
        try {
            String targetVirtual = pathArg.isEmpty() ? currentVirtualDir : sandbox.resolveVirtual(currentVirtualDir, pathArg, true);
            physicalTarget = sandbox.resolvePhysical(targetVirtual);
            if (!Files.exists(physicalTarget)) {
                sendReply("550 File or directory not found: " + pathArg + "\r\n");
                return;
            }
        } catch (SecurityException se) {
            sendReply("550 Access denied: " + se.getMessage() + "\r\n");
            return;
        }

        try (Socket dataSocket = dataChannel.establishConnection()) {
            sendReply("150 Opening BINARY mode data connection for file list.\r\n");
            try (BufferedWriter dataWriter = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream(), StandardCharsets.UTF_8))) {
                if (Files.isDirectory(physicalTarget)) {
                    try (DirectoryStream<Path> ds = Files.newDirectoryStream(physicalTarget)) {
                        for (Path entry : ds) {
                            String line = FtpPathSandbox.formatUnixListing(entry, entry.getFileName().toString());
                            dataWriter.write(line);
                        }
                    }
                } else {
                    String line = FtpPathSandbox.formatUnixListing(physicalTarget, physicalTarget.getFileName().toString());
                    dataWriter.write(line);
                }
                dataWriter.flush();
            }
            sendReply("226 Transfer complete.\r\n");
        } catch (Exception e) {
            sendReply("426 Connection closed; transfer aborted.\r\n");
        } finally {
            dataChannel.close();
        }
    }

    private void handleNlst(String pathArg) {
        Path physicalTarget;
        try {
            String targetVirtual = pathArg.isEmpty() ? currentVirtualDir : sandbox.resolveVirtual(currentVirtualDir, pathArg, true);
            physicalTarget = sandbox.resolvePhysical(targetVirtual);
            if (!Files.exists(physicalTarget)) {
                sendReply("550 File or directory not found: " + pathArg + "\r\n");
                return;
            }
        } catch (SecurityException se) {
            sendReply("550 Access denied: " + se.getMessage() + "\r\n");
            return;
        }

        try (Socket dataSocket = dataChannel.establishConnection()) {
            sendReply("150 Opening data connection for name list.\r\n");
            try (BufferedWriter dataWriter = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream(), StandardCharsets.UTF_8))) {
                if (Files.isDirectory(physicalTarget)) {
                    try (DirectoryStream<Path> ds = Files.newDirectoryStream(physicalTarget)) {
                        for (Path entry : ds) {
                            dataWriter.write(entry.getFileName().toString() + "\r\n");
                        }
                    }
                } else {
                    dataWriter.write(physicalTarget.getFileName().toString() + "\r\n");
                }
                dataWriter.flush();
            }
            sendReply("226 Transfer complete.\r\n");
        } catch (Exception e) {
            sendReply("426 Connection closed; transfer aborted.\r\n");
        } finally {
            dataChannel.close();
        }
    }

    private void handleRetr(String fileName) {
        Path physicalTarget;
        try {
            String targetVirtual = sandbox.resolveVirtual(currentVirtualDir, fileName, false);
            physicalTarget = sandbox.resolvePhysical(targetVirtual);
            if (!Files.exists(physicalTarget) || Files.isDirectory(physicalTarget)) {
                sendReply("550 File not found: " + fileName + "\r\n");
                return;
            }
        } catch (SecurityException se) {
            sendReply("550 Access denied: " + se.getMessage() + "\r\n");
            return;
        }

        try (Socket dataSocket = dataChannel.establishConnection()) {
            long size = Files.size(physicalTarget);
            sendReply("150 Opening BINARY mode data connection for " + fileName + " (" + size + " bytes).\r\n");
            try (InputStream in = Files.newInputStream(physicalTarget);
                 OutputStream out = dataSocket.getOutputStream()) {
                in.transferTo(out);
                out.flush();
            }
            sendReply("226 Transfer complete.\r\n");
        } catch (Exception e) {
            sendReply("426 Connection closed; transfer aborted.\r\n");
        } finally {
            dataChannel.close();
        }
    }

    private void handleStor(String fileName) {
        if (!FtpUserAuth.canWrite(role)) {
            sendReply("550 Permission denied: Anonymous user is read-only.\r\n");
            return;
        }
        Path physicalTarget;
        try {
            String targetVirtual = sandbox.resolveVirtual(currentVirtualDir, fileName, false);
            physicalTarget = sandbox.resolvePhysical(targetVirtual);
        } catch (SecurityException se) {
            sendReply("550 Access denied: " + se.getMessage() + "\r\n");
            return;
        }

        try (Socket dataSocket = dataChannel.establishConnection()) {
            sendReply("150 Opening BINARY mode data connection for " + fileName + ".\r\n");
            if (physicalTarget.getParent() != null) {
                Files.createDirectories(physicalTarget.getParent());
            }
            try (InputStream in = dataSocket.getInputStream();
                 OutputStream out = Files.newOutputStream(physicalTarget, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                in.transferTo(out);
                out.flush();
            }
            sendReply("226 Transfer complete.\r\n");
        } catch (Exception e) {
            sendReply("426 Connection closed; transfer aborted.\r\n");
        } finally {
            dataChannel.close();
        }
    }

    private void sendReply(String reply) {
        try {
            writer.write(reply);
            writer.flush();
        } catch (IOException ignored) {}
    }

    @Override
    public void close() {
        running = false;
        dataChannel.close();
        try {
            controlSocket.close();
        } catch (Exception ignored) {}
    }
}
