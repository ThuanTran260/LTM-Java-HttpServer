# Project: Java FTP Server & Client System (RFC 959)

## Architecture
A 100% Pure Java implementation of File Transfer Protocol (RFC 959) using Raw TCP Sockets and JDK 24 Virtual Threads, integrating an RFC-compliant FTP Server on `:2121`, a Pure Socket FTP Client Library, a Swing Dual-Pane GUI (`FtpClientApp`), integration with `WebClientApp`, comprehensive Network Security testing on service ports, and cross-compatibility with FileZilla and Windows IIS FTP (:21).

```
                      +-----------------------------+
                      |        WebClientApp         |
                      |  [HTTP Fetch & FTP Button]  |
                      +--------------+--------------+
                                     | opens
                                     v
                      +-----------------------------+
                      |        FtpClientApp         |
                      |  [Swing Dual-Pane Explorer] |
                      +--------------+--------------+
                                     | uses
                                     v
                      +-----------------------------+
                      |       client.FtpClient      |
                      |  [Raw TCP Sockets RFC 959]  |
                      +--------------+--------------+
                                     |
               +---------------------+---------------------+
               | Control :2121 (CRLF)                      | Data (PASV/EPSV/PORT)
               v                                           v
+-----------------------------+             +-----------------------------+
|    server.ftp.SimpleFtpServer|             |  server.ftp.FtpDataChannel  |
|  [Virtual Threads JDK 24]   |             |  [Ephemeral Port Lifecycle] |
+--------------+--------------+             +--------------+--------------+
               | dispatches                                | streams
               v                                           v
+-----------------------------+             +-----------------------------+
| server.ftp.FtpSessionHandler| <---------> |  server.ftp.FtpPathSandbox  |
|  [RFC 959 State Machine]    |             |  [4-Layer Chroot & Traversal]|
+-----------------------------+             +--------------+--------------+
               | checks                                    | roots to
               v                                           v
+-----------------------------+             +-----------------------------+
|    server.ftp.FtpUserAuth   |             |  ftp_workspace/server_root/ |
|  [Guest 550 / Admin RW]     |             |  [Physical Sandbox Root]    |
+-----------------------------+             +-----------------------------+
```

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | FTP Server Control Channel (:2121) | Raw ServerSocket on port 2121 dispatching sessions to JDK 24 Virtual Threads | M1 | ORIGINAL_REQUEST §R1 |
| 2 | RFC 959 Control Commands | USER, PASS, QUIT, NOOP, SYST (215 UNIX Type: L8), FEAT, PWD, TYPE (A/I) | M1 | ORIGINAL_REQUEST §R1 |
| 3 | Passive Mode Data Channel (PASV) | Ephemeral ServerSocket(0), 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2) | M1 | ORIGINAL_REQUEST §R1 |
| 4 | Extended Passive Mode (EPSV) | RFC 2428 EPSV for FileZilla/IPv4/IPv6, 229 (\|\|\|port\|) | M1 | ORIGINAL_REQUEST §R1 |
| 5 | Active Mode Data Channel (PORT) | Connect out to client IP/Port with RFC 2577 bounce-attack defense | M1 | ORIGINAL_REQUEST §R1 |
| 6 | File Listing (LIST & NLST) | UNIX `ls -l` formatted directory entries (Locale.US) for FileZilla compatibility | M1 | ORIGINAL_REQUEST §R1 |
| 7 | File Transfer (RETR & STOR) | Binary `TYPE I` raw stream transfer preserving byte-for-byte fidelity | M1 | ORIGINAL_REQUEST §R1 |
| 8 | File & Dir Management (DELE, CWD, CDUP, MKD, RMD, SIZE) | Full directory navigation, creation, deletion, and file size inquiry | M1 | ORIGINAL_REQUEST §R1 |
| 9 | User Authentication & Authorization | Anonymous (guest read-only) and Admin (full read-write-delete) | M1 | ORIGINAL_REQUEST §R1 |
| 10 | 4-Layer Chroot & Path Traversal Defense | Sandbox blocking `../`, `..\`, `%2e%2e`, `%00`, Windows drive letters -> 550 | M1 | ORIGINAL_REQUEST §R3 |
| 11 | Socket Lifecycle & Leak Prevention | AutoCloseable ephemeral sockets, instant port release after transfer | M1 | ORIGINAL_REQUEST §R3 |
| 12 | Pure Java Socket FTP Client Library | `client.FtpClient` handling connection, login, commands, PASV/PORT, transfers | M2 | ORIGINAL_REQUEST §R2 |
| 13 | Multi-line Reply & Directory Parser | `client.FtpFile` parsing both UNIX `ls -l` (Java FTP) and DOS format (IIS FTP) | M2 | ORIGINAL_REQUEST §R2 |
| 14 | Real-time Command/Response Logging | `FtpCommandListener` observer with password masking for GUI & CLI | M2 | ORIGINAL_REQUEST §R2 |
| 15 | Swing Dual-Pane GUI (`FtpClientApp`) | Dual file browser tables (Local/Remote), toolbar actions (Upload/Download/Delete/MKD) | M2 | ORIGINAL_REQUEST §R2 |
| 16 | Quick Server Toggle (:2121 vs :21) | Instant connection presets for Java FTP Server (:2121) and IIS FTP (:21) | M2 | ORIGINAL_REQUEST §R2 |
| 17 | Web Client Integration | "FTP Client..." launcher button added to `client.WebClientApp` | M2 | ORIGINAL_REQUEST §R2 |
| 18 | Batch Compilation Script | Updated `compile.bat` compiling all packages with zero errors/warnings (javac 24) | M3 | ORIGINAL_REQUEST §R4 |
| 19 | 1-Click Startup Scripts | `run_ftp_server.bat` and `run_ftp_client.bat` launchers | M3 | ORIGINAL_REQUEST §R4 |
| 20 | Automated Cross-Test Runner | `src/client/CrossTestFtpClient.java` & `run_cross_test_ftp.bat` (exit code 0 on 100% pass) | E2E/M4 | ORIGINAL_REQUEST §R4 |
| 21 | Pre-Auth Enforcement Test | Verify commands prior to login rejected with 530 | E2E/M4 | ORIGINAL_REQUEST §R3 |
| 22 | Path Traversal Security Test | Exhaustive traversal suite (`../`, `..\`, `%2e%2e`, `%00`, `C:\`) returning 550 | E2E/M4 | ORIGINAL_REQUEST §R3 |
| 23 | SHA-256 Binary Integrity Test | Byte-for-byte checksum verification across text, image, 2MB binary, and ZIP | E2E/M4 | ORIGINAL_REQUEST §R3 |
| 24 | Socket Leak Verification Test | Rapid sequential PASV transfers (50x) and verification of closed ephemeral ports | E2E/M4 | ORIGINAL_REQUEST §R3 |
| 25 | Windows IIS FTP & FileZilla Matrix | Conditional probe of port 21, and FileZilla RFC compliance verification | E2E/M4 | ORIGINAL_REQUEST §R3 |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| E2E | E2E Testing Track | Test infra, 8 test tiers (58 test assertions), `run_cross_test_ftp.bat`, `TEST_READY.md` | none | DONE |
| M1 | FTP Server Core (RFC 959) | `server.ftp.*`: `SimpleFtpServer`, `FtpSessionHandler`, `FtpDataChannel`, `FtpUserAuth`, `FtpPathSandbox` | none | DONE |
| M2 | FTP Client & Swing GUI | `client.*`: `FtpClient`, `FtpFile`, `FtpCommandListener`, `FtpReply`, `FtpClientApp`, WebClient button | M1 interface | DONE |
| M3 | System Integration & Scripts | `compile.bat`, `run_ftp_server.bat`, `run_ftp_client.bat`, launcher verification | M1, M2 | DONE |
| M4 | Final E2E Test Pass & Hardening | Execute `run_cross_test_ftp.bat` (100% PASS), adversarial coverage hardening | E2E, M1, M2, M3 | DONE |

---

## Interface Contracts

### 1. Control Wire Interface (RFC 959 Client <-> Server)
- Port: `2121`
- Line termination: `\r\n` (CRLF)
- Encoding: `StandardCharsets.UTF_8`
- Initial Greeting: `220 Simple Java FTP Server ready (JDK 24 Virtual Threads).\r\n`
- Reply codes:
  * Positive preliminary: `150 Opening BINARY mode data connection...`
  * Positive completion: `200 OK`, `215 UNIX Type: L8`, `226 Transfer complete`, `230 User logged in`, `250 Requested file action okay`, `257 "/path" is current directory`
  * Intermediate: `331 Password required for <user>`
  * Pre-auth error: `530 Please login with USER and PASS.`
  * Path/Permission error: `550 Access denied / Permission denied / File not found.`
  * Syntax/Command error: `500 Syntax error / Illegal command`, `502 Command not implemented`
  * Good-bye: `221 Goodbye.`

### 2. Data Wire Interface (Passive & Active)
- Passive: Server replies `227 Entering Passive Mode (h1,h2,h3,h4,p1,p2).` or `229 Entering Extended Passive Mode (|||port|).`
- Active: Client issues `PORT h1,h2,h3,h4,p1,p2` or `EPRT |1|ip|port|`. Server validates IP match before `200 PORT command successful.`
- Binary transfer (`TYPE I`): Raw unbuffered or 16KB buffered byte streaming without line ending conversion.
- Resource guarantee: Ephemeral server socket closed immediately upon accept; data socket closed in `finally` upon EOF.

### 3. Client Library Interface (`client.FtpClient`)
```java
public class FtpClient implements AutoCloseable {
    public void addCommandListener(FtpCommandListener listener);
    public void connect(String host, int port) throws IOException;
    public boolean login(String username, String password) throws IOException;
    public void setBinaryMode(boolean binary) throws IOException;
    public void setPassiveMode(boolean passive);
    public String printWorkingDirectory() throws IOException;
    public boolean changeWorkingDirectory(String dir) throws IOException;
    public boolean changeToParentDirectory() throws IOException;
    public List<FtpFile> listFiles(String path) throws IOException;
    public void uploadFile(File localFile, String remoteName) throws IOException;
    public void downloadFile(String remoteName, File localTarget) throws IOException;
    public boolean deleteFile(String remoteName) throws IOException;
    public boolean makeDirectory(String dirName) throws IOException;
    public boolean removeDirectory(String dirName) throws IOException;
    public void disconnect();
    public boolean isConnected();
}
```

### 4. Cross-Test Runner Contract (`client.CrossTestFtpClient`)
- CLI entrypoint: `java -cp out client.CrossTestFtpClient [options]`
- Exit code: `0` on 100% PASS; non-zero on failure.
- Auto-server bootstrap: If `:2121` is closed, spawns `server.ftp.SimpleFtpServer` in a background process, waits for listening state, runs tests, and shuts down server upon completion.
- Script wrapper: `run_cross_test_ftp.bat` exits with code 0 on success.

---

## Code Layout
```
d:\LapTrinhMang\LTM-Java-HttpServer\
├── compile.bat                         # Batch build script (javac 24)
├── run_server.bat                      # HTTP server runner
├── run_webclient.bat                   # HTTP web client runner
├── run_cross_test.bat                  # HTTP cross test runner
├── run_ftp_server.bat                  # Java FTP Server (:2121) runner
├── run_ftp_client.bat                  # Java FTP Swing Client (`FtpClientApp`) runner
├── run_cross_test_ftp.bat              # FTP Cross Test runner (100% pass)
├── ftp_workspace\
│   └── server_root\                    # Chroot sandbox physical directory
│       ├── readme.txt
│       └── sample_data.bin
├── src\
│   ├── model\                          # Existing HTTP models
│   ├── service\                        # Existing HTTP services
│   ├── mock\                           # Existing HTTP mocks
│   ├── server\
│   │   ├── SimpleHttpServer.java       # Existing HTTP server
│   │   └── ftp\
│   │       ├── SimpleFtpServer.java    # FTP Server entry point (:2121)
│   │       ├── FtpSessionHandler.java  # RFC 959 state machine & session handler
│   │       ├── FtpDataChannel.java     # Ephemeral data channel management
│   │       ├── FtpUserAuth.java        # Authentication (anonymous/admin)
│   │       └── FtpPathSandbox.java     # 4-layer Chroot & path traversal sandbox
│   └── client\
│       ├── WebClientApp.java           # Existing Swing Web client (+ FTP button)
│       ├── CrossTestClient.java        # Existing HTTP test runner
│       ├── FtpClient.java              # Pure Java Socket FTP Client library
│       ├── FtpFile.java                # Multi-format directory listing parser (UNIX + DOS)
│       ├── FtpCommandListener.java     # Command/response observer interface
│       ├── FtpReply.java               # Structured RFC 959 reply container
│       ├── FtpClientApp.java           # Swing Dual-Pane GUI FTP Client
│       └── CrossTestFtpClient.java     # Automated FTP test runner (8 tiers, 58 cases)
```
