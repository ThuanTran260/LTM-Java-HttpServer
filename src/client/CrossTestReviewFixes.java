package client;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

/**
 * Regression runner for file explorer / FTP review fixes.
 * Groups: ftp-delete (task 1). Later tasks add guest-logout and public-errors.
 */
public class CrossTestReviewFixes {

    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        String group = args.length == 0 ? "ftp-delete" : args[0];
        switch (group) {
            case "ftp-delete" -> testFtpDeleteTypes();
            default -> throw new IllegalArgumentException("Unknown group: " + group);
        }
        System.out.println("PASS " + group);
    }

    static void testFtpDeleteTypes() throws Exception {
        try (var fixture = new CrossTestFilesClient.ProcessFixture()) {
            Path ws = fixture.createWorkspace();
            var server = fixture.startFtp(ws,
                    Map.of("FTP_ADMIN_PASSWORD", "ReviewOnly_2026!"), List.of());
            Path file = server.root().resolve("keep.txt");
            Path dir = server.root().resolve("keep-dir");
            Files.writeString(file, "keep");
            Files.createDirectory(dir);
            try (FtpClient ftp = new FtpClient()) {
                ftp.connect("127.0.0.1", server.port());
                check(ftp.login("admin", "ReviewOnly_2026!"), "admin login");
                check(ftp.sendCommand("RMD keep.txt").code() == 550, "RMD rejects file");
                check(Files.readString(file).equals("keep"), "RMD preserves file bytes");
                check(ftp.sendCommand("DELE keep-dir").code() == 550, "DELE rejects dir");
                check(Files.isDirectory(dir), "DELE preserves directory");
                check(ftp.sendCommand("DELE keep.txt").code() == 250, "DELE accepts file");
                check(ftp.sendCommand("RMD keep-dir").code() == 250, "RMD accepts empty dir");
            }
        }
    }
}
