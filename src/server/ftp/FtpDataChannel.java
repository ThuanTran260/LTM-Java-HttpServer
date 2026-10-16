package server.ftp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Manages the FTP data channel lifecycle for both Passive (PASV, EPSV) and Active (PORT, EPRT) modes.
 * Ensures zero socket/port leaks by closing ephemeral server sockets immediately after accept,
 * enforcing accept/transfer timeouts, and providing strict resource cleanup.
 */
public class FtpDataChannel implements AutoCloseable {

    private ServerSocket passiveServerSocket;
    private InetSocketAddress activeTarget;
    private Socket dataSocket;
    private boolean isPassive;

    public synchronized int openPassive(InetAddress bindAddr) throws IOException {
        close();
        // Bind ephemeral port (0) with backlog 1 on the designated address
        this.passiveServerSocket = new ServerSocket(0, 1, bindAddr);
        this.passiveServerSocket.setReuseAddress(true);
        this.passiveServerSocket.setSoTimeout(30000); // 30s accept timeout
        this.isPassive = true;

        final int port = this.passiveServerSocket.getLocalPort();
        final ServerSocket ss = this.passiveServerSocket;

        // Auto-reap unaccepted passive ServerSocket after 30 seconds
        Thread.ofVirtual().name("pasv-reaper-" + port).start(() -> {
            try {
                Thread.sleep(30000);
                synchronized (FtpDataChannel.this) {
                    if (ss != null && !ss.isClosed() && ss == passiveServerSocket) {
                        try {
                            ss.close();
                        } catch (Exception ignored) {}
                        passiveServerSocket = null;
                    }
                }
            } catch (Exception ignored) {}
        });

        return port;
    }

    public synchronized void setActive(String host, int port) {
        close();
        this.activeTarget = new InetSocketAddress(host, port);
        this.isPassive = false;
    }

    public synchronized Socket establishConnection() throws IOException {
        if (isPassive) {
            if (passiveServerSocket == null || passiveServerSocket.isClosed()) {
                throw new IOException("Passive mode not prepared. Send PASV or EPSV first.");
            }
            try {
                this.dataSocket = passiveServerSocket.accept();
                this.dataSocket.setSoTimeout(60000); // 60s transfer timeout
                return this.dataSocket;
            } finally {
                // Immediately close and unbind the passive ServerSocket upon accept
                // to guarantee the ephemeral port is freed and cannot be re-entered.
                try {
                    if (passiveServerSocket != null && !passiveServerSocket.isClosed()) {
                        passiveServerSocket.close();
                    }
                } catch (Exception ignored) {}
                passiveServerSocket = null;
            }
        } else {
            if (activeTarget == null) {
                throw new IOException("Active mode not prepared. Send PORT or EPRT first.");
            }
            this.dataSocket = new Socket();
            this.dataSocket.connect(activeTarget, 10000); // 10s connect timeout
            this.dataSocket.setSoTimeout(60000); // 60s transfer timeout
            return this.dataSocket;
        }
    }

    @Override
    public synchronized void close() {
        if (passiveServerSocket != null) {
            try {
                passiveServerSocket.close();
            } catch (Exception ignored) {}
            passiveServerSocket = null;
        }
        if (dataSocket != null) {
            try {
                dataSocket.close();
            } catch (Exception ignored) {}
            dataSocket = null;
        }
        activeTarget = null;
    }
}
