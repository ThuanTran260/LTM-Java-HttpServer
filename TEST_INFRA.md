# E2E Test Infra: Java FTP Server & Client System

## Test Philosophy
- **Opaque-box & Requirement-driven**: Derived directly from `ORIGINAL_REQUEST.md` (2026-10-16T02:16:07Z) and RFC 959 standards. Independent of internal implementation details.
- **Methodology**: Systematic 4-tier methodology (Category-Partition, Boundary Value Analysis, Pairwise Combinatorial, Real-World Workload Testing) expanded into an 8-tier verification suite.
- **Deterministic & Zero-Flake**: Auto-server lifecycle management, socket timeout bounds, robust retry-connect on server startup, and unconditional port release assertions.

---

## Feature Inventory & Test Mapping
| # | Feature | Requirement Source | Tier 1 (Coverage) | Tier 2 (Boundary) | Tier 3 (Security) | Tier 4 (Workload/Cross) |
|---|---------|-------------------|:-----------------:|:-----------------:|:-----------------:|:-----------------------:|
| 1 | Server Control Connection | R1: port 2121, raw socket | ✓ (Banner 220) | ✓ (CRLF/LF) | ✓ (Idle timeout) | ✓ (Concurrent clients) |
| 2 | RFC 959 Commands | R1: USER, PASS, SYST, FEAT, PWD, TYPE, NOOP, QUIT | ✓ (All 8 cmds) | ✓ (Quoted path, params) | ✓ (Pre-auth 530) | ✓ (FileZilla syntax) |
| 3 | Passive Mode Data Channel | R1: PASV (227), EPSV (229) | ✓ (Both modes) | ✓ (Ephemeral port math) | ✓ (Port unicity) | ✓ (FileZilla EPSV pref) |
| 4 | Active Mode Data Channel | R1: PORT (200), EPRT | ✓ (PORT syntax) | ✓ (High ports) | ✓ (Bounce attack 500) | ✓ (Firewall fallback) |
| 5 | Directory Listing | R1: LIST, NLST | ✓ (List output) | ✓ (Empty dir, subdirs) | ✓ (Hidden files) | ✓ (UNIX ls -l Locale.US) |
| 6 | File Transfer (RETR/STOR) | R1: Download/Upload | ✓ (Upload/Download) | ✓ (0-byte, 2MB binary) | ✓ (Overwrite control) | ✓ (SHA-256 byte-for-byte) |
| 7 | File System Operations | R1: DELE, CWD, CDUP, MKD, RMD, SIZE | ✓ (All operations) | ✓ (Nested dirs, space) | ✓ (550 on non-empty) | ✓ (Clean state restoration) |
| 8 | User Auth & Roles | R1: anonymous vs admin | ✓ (Login flows) | ✓ (Case insensitive user) | ✓ (Anonymous read-only 550)| ✓ (Admin full write) |
| 9 | Chroot & Traversal Defense | R3: ../, %2e%2e, %00, drive letter | ✓ (Standard ..) | ✓ (Mixed / and \) | ✓ (URL/Null byte/Drive) | ✓ (Absolute boundary 550) |
| 10 | Socket & Port Lifecycle | R3: Prompt data socket closure | ✓ (Closed after xfer) | ✓ (Unaccepted PASV) | ✓ (Port starvation test) | ✓ (50x rapid transfers) |
| 11 | Windows IIS FTP & FileZilla | R2, R4: Cross testing | ✓ (Probe port 21) | ✓ (DOS listing parser) | ✓ (Banner detection) | ✓ (Graceful conditional pass) |

---

## Test Architecture
- **Test Runner Location**: `src/client/CrossTestFtpClient.java`
- **Batch Invocation**: `run_cross_test_ftp.bat` (and CLI `java -cp out client.CrossTestFtpClient`)
- **Pass/Fail Semantics**:
  * Every test prints `[PASS] Tier X.Y: <description>` or `[FAIL] Tier X.Y: <description> (expected <E>, got <A>)`.
  * Exit code `0` on 100% test pass. Non-zero (1) on any failure.
- **Server Bootstrap & Lifecycle**:
  * Auto-probes `127.0.0.1:2121`. If not listening, automatically starts `server.ftp.SimpleFtpServer` in a background daemon process.
  * Ensures clean test workspace under `ftp_workspace/test_sandbox/`.
  * Shuts down any background process upon test completion via shutdown hook and `QUIT`.

---

## Detailed Test Tiers (58 Test Cases)

### Tier 1: Connection & Protocol Handshake (7 Cases)
- `T1.1`: Connect to `:2121` and verify greeting banner starts with `220`.
- `T1.2`: Issue `SYST` and verify reply `215 UNIX Type: L8`.
- `T1.3`: Issue `FEAT` and verify multi-line reply starts with `211-` and ends with `211 End` containing `UTF8`, `PASV`, `EPSV`.
- `T1.4`: Issue `OPTS UTF8 ON` and verify `200`.
- `T1.5`: Issue `TYPE I` and verify `200 Type set to I`.
- `T1.6`: Issue `TYPE A` and verify `200 Type set to A`.
- `T1.7`: Issue `NOOP` and verify `200`.

### Tier 2: Authentication & Authorization (6 Cases)
- `T2.1`: Issue `USER anonymous` -> `331`, then `PASS guest@test.com` -> `230`.
- `T2.2`: Issue `USER admin` -> `331`, then `PASS admin123` -> `230`.
- `T2.3`: Issue invalid credentials `USER invalid` / `PASS wrong` -> `530`.
- `T2.4`: Anonymous write rejection: `STOR anon_test.txt` under anonymous -> `550 Permission denied`.
- `T2.5`: Anonymous delete rejection: `DELE any_file.txt` under anonymous -> `550 Permission denied`.
- `T2.6`: Anonymous directory creation rejection: `MKD anon_dir` under anonymous -> `550 Permission denied`.

### Tier 3: Pre-Authentication Enforcement (6 Cases)
- `T3.1`: Issue `PWD` before login -> `530 Please login`.
- `T3.2`: Issue `CWD /` before login -> `530 Please login`.
- `T3.3`: Issue `PASV` before login -> `530 Please login`.
- `T3.4`: Issue `LIST` before login -> `530 Please login`.
- `T3.5`: Issue `RETR test.txt` before login -> `530 Please login`.
- `T3.6`: Issue `STOR test.txt` before login -> `530 Please login`.

### Tier 4: Passive & Active Data Channels (6 Cases)
- `T4.1`: Issue `PASV` -> parse `227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)`, verify valid port range ($p_1 \times 256 + p_2 > 1024$).
- `T4.2`: Issue `EPSV` -> parse `229 Entering Extended Passive Mode (|||port|)`, verify valid port.
- `T4.3`: Passive LIST transfer: connect to passive port, receive data stream, verify `150` followed by `226`.
- `T4.4`: Extended Passive (EPSV) LIST transfer: verify data stream and `226`.
- `T4.5`: Active Mode `PORT`: bind local ephemeral server socket, issue `PORT`, verify `200 PORT command successful`.
- `T4.6`: Active Mode Bounce Attack defense: issue `PORT` with third-party IP -> verify rejection `500 Illegal PORT command`.

### Tier 5: Directory & File Operations (8 Cases)
- `T5.1`: `PWD` returns current directory formatted with quotes (e.g. `257 "/"`).
- `T5.2`: `MKD test_folder` creates directory and returns `257`.
- `T5.3`: `CWD test_folder` changes directory to `/test_folder` -> returns `250`.
- `T5.4`: `CDUP` changes to parent `/` -> returns `200` or `250`.
- `T5.5`: `SIZE sample.txt` returns `213 <size_in_bytes>`.
- `T5.6`: `DELE sample.txt` removes file -> returns `250`.
- `T5.7`: `RMD test_folder` removes empty folder -> returns `250`.
- `T5.8`: `RETR non_existent.txt` returns `550 File not found`.

### Tier 6: Network Security & Chroot Sandbox Defense (10 Cases)
- `T6.1`: Relative dot traversal: `CWD ../` from root `/` remains clamped at `/` (or rejected with `550`).
- `T6.2`: Deep relative traversal: `CWD ../../../../` remains clamped at `/`.
- `T6.3`: File download escape: `RETR ../../../Windows/System32/drivers/etc/hosts` -> `550 Access denied`.
- `T6.4`: File upload escape: `STOR ../../../evil.bat` -> `550 Access denied`.
- `T6.5`: Windows backslash traversal: `CWD ..\..\` -> `550 Access denied` or clamped at `/`.
- `T6.6`: URL-encoded dot traversal: `RETR %2e%2e/%2e%2e/secret.txt` -> `550 Access denied`.
- `T6.7`: Double URL-encoded traversal: `RETR %252e%252e/%252e%252e/secret.txt` -> `550 Access denied`.
- `T6.8`: Null-byte injection traversal: `RETR valid.txt%00../../boot.ini` -> `550 Access denied`.
- `T6.9`: Windows drive letter injection: `CWD C:\Windows` -> `550 Access denied`.
- `T6.10`: File deletion traversal: `DELE ../../server_root/../outside.txt` -> `550 Access denied`.

### Tier 7: Data Integrity & Binary Transfer (SHA-256) (5 Cases)
- `T7.1`: Upload & Download UTF-8 text file with mixed CRLF/LF line endings -> SHA-256 matches 100%.
- `T7.2`: Upload & Download PNG image file -> SHA-256 matches 100%.
- `T7.3`: Upload & Download 2MB pseudo-random binary file -> SHA-256 matches 100%.
- `T7.4`: Upload & Download multi-entry ZIP archive -> SHA-256 matches 100% and extracts without CRC errors.
- `T7.5`: Upload & Download 0-byte empty file -> SHA-256 matches 100% (e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855).

### Tier 8: Socket & Resource Leak Prevention & Cross-Testing (10 Cases)
- `T8.1`: Ephemeral port closure: After PASV transfer, assert connecting to the ephemeral port fails immediately (`Connection refused`).
- `T8.2`: Ephemeral ServerSocket unaccepted timeout: Send PASV but do not connect data channel -> verify ServerSocket closed after timeout.
- `T8.3`: Rapid transfer stress: Execute 50 consecutive PASV uploads/downloads in a loop without running out of sockets or ports.
- `T8.4`: Mid-transfer client abort: Open data channel, transmit 100 bytes of a 10KB file, abruptly reset connection -> verify server recovers without hanging.
- `T8.5`: Control channel QUIT: Send `QUIT` -> verify `221 Goodbye` and control socket closed.
- `T8.6`: FileZilla UNIX `ls -l` compatibility: Verify LIST output matches regex `^[bcdl-][rwxst-]{9}\s+\d+\s+\S+\s+\S+\s+\d+\s+[A-Z][a-z]{2}\s+\d+\s+(\d{2}:\d{2}|\d{4})\s+.+$` with English month names.
- `T8.7`: Client Library (`FtpClient`) end-to-end compatibility against `:2121`.
- `T8.8`: Client Library dual-parser check: Parse synthetic DOS directory listing (`05-12-24  02:30PM       <DIR>          docs`).
- `T8.9`: Windows IIS FTP (:21) probe: Check if port 21 is reachable; if yes, test login and list; if no, print diagnostic and mark as gracefully skipped.
- `T8.10`: Full matrix test exit code 0 confirmation.

---

## Coverage Thresholds
- **Tier 1 (Feature Coverage)**: ≥5 test cases per core command area (7 implemented).
- **Tier 2 (Boundary & Corner)**: Empty file, 2MB binary, null bytes, special characters (11 implemented).
- **Tier 3 (Security & Permissions)**: Path traversal variations, pre-auth, role isolation (16 implemented).
- **Tier 4 (Real-World & Integrity)**: Multi-type SHA-256 checksums, socket leak stress, FileZilla & IIS compatibility (24 implemented).
- **Total Test Cases**: 58 automated tests, all required to pass with exit code 0.
