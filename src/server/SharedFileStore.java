package server;

import java.io.*;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared storage layer for Web File Explorer and FTP Server.
 * Coordinates mutations using JVM ReentrantLocks and OS FileLock on write.lock.
 */
public class SharedFileStore {

    public record Entry(String name, String path, boolean directory,
                        Long size, Instant modified, boolean blocked) {}

    public record DirectoryPage(String path, String parent, int offset,
                                int limit, int total, List<Entry> entries) {}

    public record Download(InputStream stream, long size, String name)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }
    }

    public record RenameTicket(String source, String version) {}

    public enum DeleteKind { ANY, FILE, DIRECTORY }

    public static final class StoreException extends IOException {
        private final String code;

        public StoreException(String code, String message) {
            super(message);
            this.code = code;
        }

        public StoreException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final Path root;
    private final Path realRoot;
    private final Path stateDir;
    private final Path stagingDir;
    private final Path lockFile;
    private final long maxFileBytes;
    private final String lockKey;

    private static final ConcurrentHashMap<String, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    // Test seam for simulating AtomicMoveNotSupportedException
    boolean simulateAtomicUnsupported = false;

    public void setSimulateAtomicUnsupported(boolean simulate) {
        this.simulateAtomicUnsupported = simulate;
    }

    public SharedFileStore(Path root, long maxFileBytes) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("Root cannot be null");
        }
        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException("maxFileBytes must be positive: " + maxFileBytes);
        }
        this.maxFileBytes = maxFileBytes;

        Path normRoot = root.toAbsolutePath().normalize();
        if (!Files.exists(normRoot)) {
            Files.createDirectories(normRoot);
        }
        // Verify root safety and pin realRoot
        SafePaths.resolveSafe(normRoot, "/");
        this.root = normRoot;
        this.realRoot = normRoot.toRealPath();

        // State directory is sibling <root-name>.state
        Path parent = normRoot.getParent();
        String stateDirName = normRoot.getFileName().toString() + ".state";
        this.stateDir = (parent != null ? parent.resolve(stateDirName) : Paths.get(stateDirName)).toAbsolutePath().normalize();
        if (!Files.exists(stateDir)) {
            Files.createDirectories(stateDir);
        }
        if (SafePaths.isLinkOrReparsePoint(stateDir)) {
            throw new SecurityException("State directory cannot be a link or junction: " + stateDir);
        }

        this.stagingDir = stateDir.resolve("staging");
        if (!Files.exists(stagingDir)) {
            Files.createDirectories(stagingDir);
        }

        this.lockFile = stateDir.resolve("write.lock");
        if (!Files.exists(lockFile)) {
            Files.createFile(lockFile);
        }

        this.lockKey = realRoot.toString().toLowerCase(Locale.ROOT);
    }

    public Path root() {
        return root;
    }

    public long maxFileBytes() {
        return maxFileBytes;
    }

    public DirectoryPage list(String virtualPath, int offset, int limit) throws IOException {
        Path dir = SafePaths.resolveSafe(root, virtualPath);
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            throw new StoreException("NOT_FOUND", "Directory not found: " + virtualPath);
        }
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            throw new StoreException("FORBIDDEN", "Path is not a directory: " + virtualPath);
        }
        if (SafePaths.isLinkOrReparsePoint(dir)) {
            throw new StoreException("FORBIDDEN", "Directory link or junction access forbidden: " + virtualPath);
        }

        List<Entry> allEntries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            int count = 0;
            for (Path child : stream) {
                count++;
                if (count > 10000) {
                    throw new StoreException("TOO_MANY_ENTRIES", "Directory exceeds maximum limit of 10,000 entries");
                }
                String name = child.getFileName().toString();
                String childVirtual = buildChildVirtual(virtualPath, name);

                if (SafePaths.isLinkOrReparsePoint(child) || SafePaths.isDanglingLink(child)) {
                    allEntries.add(new Entry(name, childVirtual, false, null, null, true));
                } else {
                    BasicFileAttributes attrs = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    boolean isDir = attrs.isDirectory();
                    Long size = isDir ? null : attrs.size();
                    Instant mod = attrs.lastModifiedTime().toInstant();
                    allEntries.add(new Entry(name, childVirtual, isDir, size, mod, false));
                }
            }
        }

        // Sort: directories first, then alphabetical case-insensitive
        allEntries.sort((a, b) -> {
            if (a.directory() != b.directory()) {
                return a.directory() ? -1 : 1;
            }
            return a.name().compareToIgnoreCase(b.name());
        });

        int total = allEntries.size();
        int clampedLimit = Math.max(1, Math.min(200, limit));
        int clampedOffset = Math.max(0, offset);

        List<Entry> paged;
        if (clampedOffset >= total) {
            paged = Collections.emptyList();
        } else {
            int end = Math.min(total, clampedOffset + clampedLimit);
            paged = allEntries.subList(clampedOffset, end);
        }

        String normVirt = normalizeVirtual(virtualPath);
        String parentVirt = normVirt.equals("/") ? null : getParentVirtual(normVirt);

        return new DirectoryPage(normVirt, parentVirt, clampedOffset, clampedLimit, total, paged);
    }

    public Download openDownload(String virtualPath) throws IOException {
        Path target = SafePaths.resolveSafe(root, virtualPath);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new StoreException("NOT_FOUND", "File not found: " + virtualPath);
        }
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new StoreException("FORBIDDEN", "Cannot download a directory: " + virtualPath);
        }
        if (SafePaths.isLinkOrReparsePoint(target)) {
            throw new StoreException("FORBIDDEN", "Cannot download a link or junction: " + virtualPath);
        }
        long size = Files.size(target);
        InputStream stream = Files.newInputStream(target, StandardOpenOption.READ);
        return new Download(stream, size, target.getFileName().toString());
    }

    public Entry upload(String virtualPath, InputStream input, long expectedBytes, boolean replace) throws IOException {
        Path targetPath = SafePaths.resolveSafe(root, virtualPath);
        if (targetPath.equals(root)) {
            throw new StoreException("FORBIDDEN", "Cannot upload to root directory");
        }

        Path parent = targetPath.getParent();
        if (parent == null || !Files.exists(parent, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new StoreException("NOT_FOUND", "Parent directory does not exist for: " + virtualPath);
        }

        Object parentIdentityBefore = directoryIdentity(parent);
        if (parentIdentityBefore == null) {
            throw new StoreException("CONFLICT", "Cannot determine parent directory identity");
        }

        boolean targetExistedBefore = Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS);
        if (targetExistedBefore && !replace) {
            throw new StoreException("CONFLICT", "Target file already exists and replace is false");
        }
        if (targetExistedBefore && Files.isDirectory(targetPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new StoreException("CONFLICT", "Target is an existing directory");
        }

        String targetHashBefore = (targetExistedBefore && replace) ? computeSha256(targetPath) : null;

        // Stage upload outside root in staging directory
        Path stagingFile = Files.createTempFile(stagingDir, "upload-", ".tmp");
        try {
            long actualBytes = 0;
            try (OutputStream out = Files.newOutputStream(stagingFile, StandardOpenOption.WRITE)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = input.read(buf)) != -1) {
                    actualBytes += r;
                    if (actualBytes > maxFileBytes) {
                        throw new StoreException("TOO_LARGE", "Uploaded file exceeds maximum limit of " + maxFileBytes + " bytes");
                    }
                    if (expectedBytes >= 0 && actualBytes > expectedBytes) {
                        throw new StoreException("TOO_LARGE", "Uploaded bytes exceeded declared expected size: " + expectedBytes);
                    }
                    out.write(buf, 0, r);
                }
            } catch (StoreException se) {
                throw se;
            } catch (IOException ioe) {
                throw new StoreException("CONFLICT", "Transfer interrupted: " + ioe.getMessage(), ioe);
            }

            if (expectedBytes >= 0 && actualBytes != expectedBytes) {
                throw new StoreException("CONFLICT", "Uploaded size mismatch: expected " + expectedBytes + " bytes, got " + actualBytes);
            }

            // Commit under JVM & OS lock
            final long committedSize = actualBytes;
            return withLock(() -> {
                // Revalidate parent
                if (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
                    throw new StoreException("CONFLICT", "Parent directory no longer exists");
                }
                Object currParentIdentity = directoryIdentity(parent);
                if (currParentIdentity == null || !Objects.equals(parentIdentityBefore, currParentIdentity)) {
                    throw new StoreException("CONFLICT", "Parent directory identity changed");
                }

                // Revalidate target
                boolean targetNowExists = Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS);
                if (!replace && targetNowExists) {
                    throw new StoreException("CONFLICT", "Target already exists and replace is false");
                }
                if (replace) {
                    if (targetExistedBefore != targetNowExists) {
                        throw new StoreException("CONFLICT", "Target file state changed concurrently");
                    }
                    if (targetNowExists) {
                        String currHash = computeSha256(targetPath);
                        if (!Objects.equals(targetHashBefore, currHash)) {
                            throw new StoreException("CONFLICT", "Target file modified concurrently");
                        }
                    }
                }

                if (simulateAtomicUnsupported) {
                    throw new StoreException("ATOMIC_UNSUPPORTED", "Simulated unsupported atomic move");
                }

                try {
                    if (replace && targetNowExists) {
                        Files.move(stagingFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        Files.move(stagingFile, targetPath, StandardCopyOption.ATOMIC_MOVE);
                    }
                } catch (AtomicMoveNotSupportedException e) {
                    throw new StoreException("ATOMIC_UNSUPPORTED", "Atomic move not supported on this volume", e);
                }

                BasicFileAttributes newAttrs = Files.readAttributes(targetPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                return new Entry(targetPath.getFileName().toString(), normalizeVirtual(virtualPath), false,
                        newAttrs.size(), newAttrs.lastModifiedTime().toInstant(), false);
            });

        } finally {
            try {
                Files.deleteIfExists(stagingFile);
            } catch (IOException ignored) {}
        }
    }

    public Entry mkdir(String virtualPath) throws IOException {
        Path target = SafePaths.resolveSafe(root, virtualPath);
        if (target.equals(root)) {
            throw new StoreException("FORBIDDEN", "Cannot create root directory");
        }
        Path parent = target.getParent();
        if (parent == null || !Files.exists(parent, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new StoreException("NOT_FOUND", "Parent directory does not exist for: " + virtualPath);
        }

        return withLock(() -> {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new StoreException("CONFLICT", "Directory or file already exists: " + virtualPath);
            }
            Files.createDirectory(target);
            BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return new Entry(target.getFileName().toString(), normalizeVirtual(virtualPath), true,
                    null, attrs.lastModifiedTime().toInstant(), false);
        });
    }

    public void delete(String virtualPath) throws IOException {
        delete(virtualPath, DeleteKind.ANY);
    }

    public void delete(String virtualPath, DeleteKind kind) throws IOException {
        withLock(() -> {
            Path target = SafePaths.resolveSafe(root, virtualPath);
            if (target.equals(root)) {
                throw new StoreException("FORBIDDEN", "Cannot delete root directory");
            }
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new StoreException("NOT_FOUND", "Target not found: " + virtualPath);
            }
            boolean directory = Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS);
            boolean regular = Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
            if ((kind == DeleteKind.FILE && !regular)
                    || (kind == DeleteKind.DIRECTORY && !directory)) {
                throw new StoreException("FORBIDDEN", "Target type does not match command");
            }
            if (directory) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(target)) {
                    if (ds.iterator().hasNext()) {
                        throw new StoreException("NOT_EMPTY", "Directory is not empty: " + virtualPath);
                    }
                }
            }
            try {
                Files.delete(target);
            } catch (DirectoryNotEmptyException dne) {
                throw new StoreException("NOT_EMPTY", "Directory is not empty: " + virtualPath);
            } catch (java.nio.file.FileSystemException fse) {
                throw new StoreException("CONFLICT", "Target file is currently in use or locked: " + fse.getMessage(), fse);
            }
            return null;
        });
    }

    public RenameTicket prepareRename(String sourceVirtual) throws IOException {
        Path source = SafePaths.resolveSafe(root, sourceVirtual);
        if (source.equals(root)) {
            throw new StoreException("FORBIDDEN", "Cannot rename root directory");
        }
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new StoreException("NOT_FOUND", "Source not found: " + sourceVirtual);
        }

        BasicFileAttributes attrs = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        String version;
        if (attrs.isDirectory()) {
            Object fileKey = directoryIdentity(source);
            version = "DIR:" + (fileKey != null ? fileKey.toString() : "") + ":" + attrs.lastModifiedTime().toMillis() + ":" + source.getFileName();
        } else {
            version = "FILE:" + computeSha256(source) + ":" + attrs.size() + ":" + attrs.lastModifiedTime().toMillis();
        }
        return new RenameTicket(normalizeVirtual(sourceVirtual), version);
    }

    public Entry rename(RenameTicket ticket, String targetVirtual) throws IOException {
        if (ticket == null || ticket.source() == null || ticket.version() == null) {
            throw new StoreException("BAD_REQUEST", "Invalid rename ticket");
        }
        Path source = SafePaths.resolveSafe(root, ticket.source());
        Path target = SafePaths.resolveSafe(root, targetVirtual);

        if (source.equals(root) || target.equals(root)) {
            throw new StoreException("FORBIDDEN", "Cannot rename root directory");
        }
        Path targetParent = target.getParent();
        if (targetParent == null || !Files.exists(targetParent, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(targetParent, LinkOption.NOFOLLOW_LINKS)) {
            throw new StoreException("NOT_FOUND", "Target parent directory does not exist");
        }

        return withLock(() -> {
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                throw new StoreException("NOT_FOUND", "Source no longer exists: " + ticket.source());
            }

            BasicFileAttributes currAttrs = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            String currVersion;
            if (currAttrs.isDirectory()) {
                Object fileKey = directoryIdentity(source);
                currVersion = "DIR:" + (fileKey != null ? fileKey.toString() : "") + ":" + currAttrs.lastModifiedTime().toMillis() + ":" + source.getFileName();
            } else {
                currVersion = "FILE:" + computeSha256(source) + ":" + currAttrs.size() + ":" + currAttrs.lastModifiedTime().toMillis();
            }
            if (!ticket.version().equals(currVersion)) {
                throw new StoreException("CONFLICT", "Source file was modified concurrently before rename");
            }

            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new StoreException("CONFLICT", "Target already exists: " + targetVirtual);
            }

            if (simulateAtomicUnsupported) {
                throw new StoreException("ATOMIC_UNSUPPORTED", "Simulated unsupported atomic move");
            }

            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                throw new StoreException("ATOMIC_UNSUPPORTED", "Atomic move not supported on this volume", e);
            } catch (java.nio.file.FileSystemException fse) {
                throw new StoreException("CONFLICT", "File is currently in use or locked: " + fse.getMessage(), fse);
            }

            BasicFileAttributes newAttrs = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return new Entry(target.getFileName().toString(), normalizeVirtual(targetVirtual), newAttrs.isDirectory(),
                    newAttrs.isDirectory() ? null : newAttrs.size(), newAttrs.lastModifiedTime().toInstant(), false);
        });
    }

    public Entry rename(String sourceVirtual, String targetVirtual) throws IOException {
        RenameTicket ticket = prepareRename(sourceVirtual);
        return rename(ticket, targetVirtual);
    }

    // =========================================================================
    // LOCKING & UTILITY HELPERS
    // =========================================================================

    @FunctionalInterface
    private interface MutationAction<T> {
        T execute() throws IOException;
    }

    private <T> T withLock(MutationAction<T> action) throws IOException {
        ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(lockKey, k -> new ReentrantLock());
        boolean jvmAcquired = false;
        try {
            jvmAcquired = jvmLock.tryLock(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StoreException("BUSY", "JVM lock wait interrupted");
        }
        if (!jvmAcquired) {
            throw new StoreException("BUSY", "Storage is busy (concurrent JVM mutation)");
        }

        try {
            long deadline = System.currentTimeMillis() + 5000;
            FileLock osLock = null;
            FileChannel channel = null;
            try {
                channel = FileChannel.open(lockFile, StandardOpenOption.READ, StandardOpenOption.WRITE);
                while (System.currentTimeMillis() < deadline) {
                    try {
                        osLock = channel.tryLock();
                        if (osLock != null) {
                            break;
                        }
                    } catch (OverlappingFileLockException ignored) {
                        // Avoided by JVM lock, but retry safely if encountered
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new StoreException("BUSY", "OS lock wait interrupted");
                    }
                }

                if (osLock == null) {
                    throw new StoreException("BUSY", "Storage is busy (process lock timeout)");
                }

                // Re-verify root directory integrity
                if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(realRoot)) {
                    throw new StoreException("FORBIDDEN", "Root directory moved or compromised");
                }

                return action.execute();

            } finally {
                if (osLock != null) {
                    try { osLock.release(); } catch (IOException ignored) {}
                }
                if (channel != null) {
                    try { channel.close(); } catch (IOException ignored) {}
                }
            }
        } finally {
            jvmLock.unlock();
        }
    }

    public static String computeSha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                digest.update(buf, 0, r);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String normalizeVirtual(String v) {
        if (v == null || v.isEmpty() || v.equals("/")) {
            return "/";
        }
        String clean = v.replace('\\', '/');
        if (!clean.startsWith("/")) {
            clean = "/" + clean;
        }
        while (clean.length() > 1 && clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    private static String buildChildVirtual(String parentVirt, String name) {
        String p = normalizeVirtual(parentVirt);
        return p.equals("/") ? "/" + name : p + "/" + name;
    }

    private static String getParentVirtual(String virtualPath) {
        String clean = normalizeVirtual(virtualPath);
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return clean.substring(0, lastSlash);
    }

    /**
     * Reliable identity for a directory that must stay the same between an upload's
     * staging and commit steps. The canonical path is revalidated through
     * SafePaths.resolveSafe (link/junction rejection, containment) before any attribute
     * read. Returns null when no reliable identity is available; callers must fail closed
     * with CONFLICT. This does not promise protection from an OS user racing filesystem
     * mutation between the validation and the read.
     */
    Object directoryIdentity(Path directory) throws IOException {
        if (directory == null) {
            return null;
        }
        Path normalized = directory.toAbsolutePath().normalize();
        String relative;
        try {
            relative = root.relativize(normalized).toString();
        } catch (IllegalArgumentException e) {
            return null; // different root or drive; no identity to compare
        }
        Path canonical = SafePaths.resolveSafe(root, relative);
        if (SafePaths.isLinkOrReparsePoint(canonical)) {
            throw new StoreException("FORBIDDEN", "Parent directory is a link or junction");
        }
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(canonical, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException e) {
            return null;
        }
        if (!attrs.isDirectory() || attrs.isSymbolicLink() || attrs.isOther()) {
            return null;
        }
        Object fileKey = attrs.fileKey();
        if (fileKey == null) {
            // A provider may expose the key only on the link-following read; links and
            // junctions were explicitly rejected above, so this read cannot escape them.
            try {
                fileKey = Files.readAttributes(canonical, BasicFileAttributes.class).fileKey();
            } catch (IOException ignored) {
                // Fall through to the Windows file identity below.
            }
        }
        if (fileKey != null) {
            return fileKey;
        }
        return WindowsDirectoryFileId.read(canonical);
    }

    /**
     * Reliable directory identity on Windows. The JDK Windows provider hardcodes
     * BasicFileAttributes.fileKey() to null (sun/nio/fs/WindowsFileAttributes.fileKey),
     * so this reads the same OS file identity that Files.isSameFile compares on Windows:
     * the volume serial number and 128-bit file ID returned by
     * GetFileInformationByHandleEx(FileIdInfo). Any failure (unsupported filesystem,
     * future JDK blocking native access) returns null so uploads fail closed with
     * CONFLICT instead of falling back to timestamps. Uses only JDK standard APIs.
     */
    private static final class WindowsDirectoryFileId {

        private static final int FILE_READ_ATTRIBUTES = 0x80;
        private static final int SHARE_READ_WRITE_DELETE = 0x7;
        private static final int OPEN_EXISTING = 3;
        private static final int FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;
        private static final int FILE_FLAG_OPEN_REPARSE_POINT = 0x00200000;
        private static final int FILE_ID_INFO_CLASS = 18;
        private static final int FILE_ID_INFO_SIZE = 24;

        private static final Arena LIBRARY_ARENA;
        private static final MethodHandle CREATE_FILE;
        private static final MethodHandle GET_FILE_INFO;
        private static final MethodHandle CLOSE_HANDLE;
        private static final boolean AVAILABLE;

        static {
            Arena libraryArena = null;
            MethodHandle createFile = null;
            MethodHandle getFileInfo = null;
            MethodHandle closeHandle = null;
            boolean available = false;
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
                try {
                    Linker linker = Linker.nativeLinker();
                    libraryArena = Arena.ofShared();
                    SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32.dll", libraryArena);
                    createFile = linker.downcallHandle(kernel32.find("CreateFileW").orElseThrow(),
                            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
                    getFileInfo = linker.downcallHandle(kernel32.find("GetFileInformationByHandleEx").orElseThrow(),
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
                    closeHandle = linker.downcallHandle(kernel32.find("CloseHandle").orElseThrow(),
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
                    available = true;
                } catch (Throwable ignored) {
                    available = false;
                }
            }
            LIBRARY_ARENA = libraryArena;
            CREATE_FILE = createFile;
            GET_FILE_INFO = getFileInfo;
            CLOSE_HANDLE = closeHandle;
            AVAILABLE = available;
        }

        private WindowsDirectoryFileId() {}

        static Object read(Path directory) {
            if (!AVAILABLE) {
                return null;
            }
            try (Arena arena = Arena.ofConfined()) {
                String path = directory.toAbsolutePath().normalize().toString();
                if (path.length() >= 248 && !path.startsWith("\\\\")) {
                    path = "\\\\?\\" + path; // long local path, mirrors WindowsPath
                }
                MemorySegment wide = arena.allocate((path.length() + 1) * 2L);
                for (int i = 0; i < path.length(); i++) {
                    wide.setAtIndex(ValueLayout.JAVA_SHORT, i, (short) path.charAt(i));
                }
                wide.setAtIndex(ValueLayout.JAVA_SHORT, path.length(), (short) 0);

                MemorySegment handle = (MemorySegment) CREATE_FILE.invokeExact(wide,
                        FILE_READ_ATTRIBUTES, SHARE_READ_WRITE_DELETE, MemorySegment.NULL, OPEN_EXISTING,
                        FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OPEN_REPARSE_POINT, MemorySegment.NULL);
                if (handle.address() == -1L) {
                    return null;
                }
                try {
                    MemorySegment info = arena.allocate(FILE_ID_INFO_SIZE);
                    if ((int) GET_FILE_INFO.invokeExact(handle, FILE_ID_INFO_CLASS, info, FILE_ID_INFO_SIZE) == 0) {
                        return null;
                    }
                    long volumeSerial = info.get(ValueLayout.JAVA_LONG, 0);
                    byte[] fileId = new byte[16];
                    MemorySegment.copy(info, ValueLayout.JAVA_BYTE, 8, fileId, 0, 16);
                    return "win-file-id:" + Long.toHexString(volumeSerial) + ":" + HexFormat.of().formatHex(fileId);
                } finally {
                    int ignored = (int) CLOSE_HANDLE.invokeExact(handle);
                }
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
