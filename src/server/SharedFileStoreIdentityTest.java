package server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Identity regression tests for upload parent directory handling (Task 7).
 * Plain Java main; any failed check throws AssertionError.
 */
public class SharedFileStoreIdentityTest {

    public static void main(String[] args) throws Exception {
        Path workspace = Files.createTempDirectory("store-identity-");
        try {
            missingIdentityRejectsBeforePublishing(workspace);
            missingIdentityReplaceKeepsOriginalBytes(workspace);
            missingIdentityRejectsBeforeReadingBytes(workspace);
            identityChangeAtCommitFailsUpload(workspace);
            identityLossAtCommitFailsUpload(workspace);
            equalTimestampsUnequalKeysFail(workspace);
            realProviderIdentityIsStableAndDistinct(workspace);
            realProviderUploadPublishes(workspace);
            System.out.println("PASS SharedFileStoreIdentityTest");
        } finally {
            deleteRecursively(workspace);
        }
    }

    /** Missing parent identity must fail with CONFLICT and publish nothing. */
    static void missingIdentityRejectsBeforePublishing(Path workspace) throws Exception {
        Path root = freshRoot(workspace, "missing-identity");
        SharedFileStore noIdentity = noIdentityStore(root);
        boolean rejected = false;
        try {
            noIdentity.upload("/new.bin", new ByteArrayInputStream(new byte[]{1}), 1, false);
        } catch (SharedFileStore.StoreException e) {
            rejected = e.code().equals("CONFLICT");
        }
        if (!rejected || Files.exists(root.resolve("new.bin"))) {
            throw new AssertionError("Upload must fail closed without parent identity");
        }
        assertStagingEmpty(root);
    }

    /** replace=true with an existing destination must leave original bytes untouched. */
    static void missingIdentityReplaceKeepsOriginalBytes(Path workspace) throws Exception {
        Path root = freshRoot(workspace, "replace-keeps-original");
        Files.write(root.resolve("keep.bin"), new byte[]{5, 5, 5});
        SharedFileStore noIdentity = noIdentityStore(root);
        boolean rejected = false;
        try {
            noIdentity.upload("/keep.bin", new ByteArrayInputStream(new byte[]{9}), 1, true);
        } catch (SharedFileStore.StoreException e) {
            rejected = e.code().equals("CONFLICT");
        }
        if (!rejected) {
            throw new AssertionError("Replace upload must fail closed without parent identity");
        }
        if (!Arrays.equals(Files.readAllBytes(root.resolve("keep.bin")), new byte[]{5, 5, 5})) {
            throw new AssertionError("Original destination bytes must be unchanged");
        }
        assertStagingEmpty(root);
    }

    /** Missing identity must reject before any request bytes are read. */
    static void missingIdentityRejectsBeforeReadingBytes(Path workspace) throws Exception {
        Path root = freshRoot(workspace, "reject-before-read");
        AtomicInteger reads = new AtomicInteger();
        InputStream counting = new InputStream() {
            @Override
            public int read() {
                reads.incrementAndGet();
                return 7;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                reads.incrementAndGet();
                return 1;
            }
        };
        SharedFileStore noIdentity = noIdentityStore(root);
        boolean rejected = false;
        try {
            noIdentity.upload("/counted.bin", counting, 1, false);
        } catch (SharedFileStore.StoreException e) {
            rejected = e.code().equals("CONFLICT");
        }
        if (!rejected) {
            throw new AssertionError("Upload must fail closed without parent identity");
        }
        if (reads.get() != 0) {
            throw new AssertionError("Input stream must not be read when parent identity is missing");
        }
        assertStagingEmpty(root);
    }

    /** Identity that changes between staging and commit must fail; old bytes stay intact. */
    static void identityChangeAtCommitFailsUpload(Path workspace) throws Exception {
        Path root = freshRoot(workspace, "identity-changes");
        Files.write(root.resolve("stable.bin"), new byte[]{1, 2, 3});
        AtomicInteger calls = new AtomicInteger();
        SharedFileStore flaky = new SharedFileStore(root, 1024) {
            @Override
            Object directoryIdentity(Path directory) {
                return calls.getAndIncrement() == 0 ? "key-A" : "key-B";
            }
        };
        boolean rejected = false;
        try {
            flaky.upload("/stable.bin", new ByteArrayInputStream(new byte[]{9, 9, 9, 9}), 4, true);
        } catch (SharedFileStore.StoreException e) {
            rejected = e.code().equals("CONFLICT");
        }
        if (!rejected) {
            throw new AssertionError("Changed parent identity must fail the upload");
        }
        if (!Arrays.equals(Files.readAllBytes(root.resolve("stable.bin")), new byte[]{1, 2, 3})) {
            throw new AssertionError("Destination bytes must stay intact after identity change");
        }
        assertStagingEmpty(root);
    }

    /** Identity that disappears at commit (null) must fail and clean staging. */
    static void identityLossAtCommitFailsUpload(Path workspace) throws Exception {
        Path root = freshRoot(workspace, "identity-lost");
        Files.write(root.resolve("stable.bin"), new byte[]{7, 7});
        AtomicInteger calls = new AtomicInteger();
        SharedFileStore flaky = new SharedFileStore(root, 1024) {
            @Override
            Object directoryIdentity(Path directory) {
                return calls.getAndIncrement() == 0 ? "key-A" : null;
            }
        };
        boolean rejected = false;
        try {
            flaky.upload("/stable.bin", new ByteArrayInputStream(new byte[]{9, 9}), 2, true);
        } catch (SharedFileStore.StoreException e) {
            rejected = e.code().equals("CONFLICT");
        }
        if (!rejected) {
            throw new AssertionError("Lost parent identity must fail the upload");
        }
        if (!Arrays.equals(Files.readAllBytes(root.resolve("stable.bin")), new byte[]{7, 7})) {
            throw new AssertionError("Destination bytes must stay intact after identity loss");
        }
        assertStagingEmpty(root);
    }

    /** Unequal keys with unchanged directory timestamps must fail: no timestamp fallback. */
    static void equalTimestampsUnequalKeysFail(Path workspace) throws Exception {
        Path root = freshRoot(workspace, "equal-timestamps");
        Files.write(root.resolve("stable.bin"), new byte[]{4, 4});
        FileTime creationBefore = Files.readAttributes(root, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).creationTime();
        AtomicInteger calls = new AtomicInteger();
        SharedFileStore flaky = new SharedFileStore(root, 1024) {
            @Override
            Object directoryIdentity(Path directory) {
                return calls.getAndIncrement() == 0 ? "key-A" : "key-B";
            }
        };
        boolean rejected = false;
        try {
            flaky.upload("/stable.bin", new ByteArrayInputStream(new byte[]{6, 6}), 2, true);
        } catch (SharedFileStore.StoreException e) {
            rejected = e.code().equals("CONFLICT");
        }
        FileTime creationAfter = Files.readAttributes(root, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).creationTime();
        if (!creationBefore.equals(creationAfter)) {
            throw new AssertionError("Test setup: directory timestamps must be unchanged");
        }
        if (!rejected) {
            throw new AssertionError("Unequal identities must fail even with equal timestamps");
        }
        if (!Arrays.equals(Files.readAllBytes(root.resolve("stable.bin")), new byte[]{4, 4})) {
            throw new AssertionError("Destination bytes must stay intact");
        }
        assertStagingEmpty(root);
    }

    /**
     * Real provider: identity must exist on this platform, be stable for one directory,
     * differ for distinct directories, detect replacement and follow renames.
     */
    static void realProviderIdentityIsStableAndDistinct(Path workspace) throws Exception {
        Path root = freshRoot(workspace, "provider-identity");
        SharedFileStore store = new SharedFileStore(root, 1024);

        Object rootIdentity = store.directoryIdentity(root);
        if (rootIdentity == null) {
            throw new AssertionError(
                    "Platform/provider must expose a reliable directory identity; uploads would fail closed");
        }
        if (!rootIdentity.equals(store.directoryIdentity(root))) {
            throw new AssertionError("Identity must be stable for the same directory");
        }

        Path other = Files.createDirectories(root.resolve("other-dir"));
        Object otherIdentity = store.directoryIdentity(other);
        if (otherIdentity == null || otherIdentity.equals(rootIdentity)) {
            throw new AssertionError("Distinct directories must have distinct identities");
        }

        Path swap = Files.createDirectories(root.resolve("swap-dir"));
        Object swapBefore = store.directoryIdentity(swap);
        Files.move(swap, root.resolve("swap-moved"));
        Files.createDirectories(root.resolve("swap-dir"));
        Object swapAfter = store.directoryIdentity(swap);
        if (swapAfter == null || swapBefore.equals(swapAfter)) {
            throw new AssertionError("Replaced directory must not keep the old identity");
        }
        if (!swapBefore.equals(store.directoryIdentity(root.resolve("swap-moved")))) {
            throw new AssertionError("Identity must follow the directory across rename");
        }
    }

    /** Real provider: a normal Windows/provider upload must still publish successfully. */
    static void realProviderUploadPublishes(Path workspace) throws Exception {
        Path root = freshRoot(workspace, "provider-upload");
        SharedFileStore store = new SharedFileStore(root, 1024);
        store.upload("/real.bin", new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, false);
        if (!Arrays.equals(Files.readAllBytes(root.resolve("real.bin")), new byte[]{1, 2, 3})) {
            throw new AssertionError("Upload with provider identity must publish the expected bytes");
        }
        assertStagingEmpty(root);
    }

    static Path freshRoot(Path workspace, String name) throws IOException {
        return Files.createDirectories(workspace.resolve(name).resolve("root"));
    }

    static SharedFileStore noIdentityStore(Path root) throws IOException {
        return new SharedFileStore(root, 1024) {
            @Override
            Object directoryIdentity(Path directory) {
                return null;
            }
        };
    }

    static void assertStagingEmpty(Path root) throws IOException {
        Path staging = root.getParent().resolve(root.getFileName() + ".state").resolve("staging");
        try (var stream = Files.list(staging)) {
            if (stream.findAny().isPresent()) {
                throw new AssertionError("Staging directory must be empty: " + staging);
            }
        }
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path p : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
