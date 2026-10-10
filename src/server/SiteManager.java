package server;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Quan ly nhieu web (site) tren cung mot Java Server: moi site co mot thu muc rieng va chay tren mot cong rieng
 * (giong IIS Sites/bindings). Them / bat / tat / xoa luc dang chay, luu vao sites.json de khoi dong lai van con.
 * Moi site la mot HttpServer rieng phuc vu file tinh cua thu muc <sitesRoot>/<ten>/.
 */
public final class SiteManager {
    public static final int MIN_PORT = 1024;
    public static final int MAX_PORT = 65535;

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{[^{}]*\\}");
    private static final Pattern DISABLED_PATTERN = Pattern.compile("\"enabled\"\\s*:\\s*false");

    /** Loi nghiep vu kem ma HTTP: 400 dau vao sai, 404 khong co site, 409 trung ten/cong hoac cong dang ban, 500 loi ghi file. */
    public static final class SiteException extends Exception {
        private final int status;

        public SiteException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /** Anh chup trang thai mot site tai thoi diem goi. */
    public record SiteView(String name, int port, boolean enabled, boolean running) {
        public String url() {
            return "http://localhost:" + port + "/";
        }

        public String toJson() {
            return "{\"name\":\"" + name + "\",\"port\":" + port + ",\"enabled\":" + enabled
                + ",\"running\":" + running + ",\"url\":\"" + url() + "\"}";
        }
    }

    private static final class Site {
        final String name;
        final int port;
        boolean enabled;
        HttpServer server;

        Site(String name, int port, boolean enabled) {
            this.name = name;
            this.port = port;
            this.enabled = enabled;
        }
    }

    private final Path sitesRoot;
    private final Path configFile;
    private final int reservedPort;
    private final Map<String, Site> sites = new LinkedHashMap<>();

    public SiteManager(Path sitesRoot, Path configFile, int reservedPort) {
        this.sitesRoot = sitesRoot;
        this.configFile = configFile;
        this.reservedPort = reservedPort;
    }

    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    /** Doc sites.json va bat cac site enabled. Muc sai / site khong bat duoc chi bi ghi log, khong lam hong ca server. */
    public synchronized void load() {
        if (!Files.isRegularFile(configFile)) {
            return;
        }
        String json;
        try {
            json = Files.readString(configFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[SITES] Khong doc duoc " + configFile + ": " + e.getMessage());
            return;
        }
        Matcher m = OBJECT_PATTERN.matcher(json);
        while (m.find()) {
            String obj = m.group();
            String name = RouterHandler.parseJsonString(obj, "name");
            Integer port = RouterHandler.parseJsonInt(obj, "port");
            if (!isValidName(name) || port == null || port < MIN_PORT || port > MAX_PORT
                    || port == reservedPort || sites.containsKey(name) || portTaken(port)) {
                System.err.println("[SITES] Bo qua muc khong hop le trong " + configFile + ": " + obj);
                continue;
            }
            Site s = new Site(name, port, !DISABLED_PATTERN.matcher(obj).find());
            sites.put(name, s);
            try {
                prepareFolder(s);
            } catch (IOException e) {
                System.err.println("[SITES] Khong tao duoc thu muc cua '" + name + "': " + e.getMessage());
            }
            if (s.enabled) {
                try {
                    startSite(s);
                } catch (SiteException e) {
                    System.err.println("[SITES] Khong bat duoc site '" + name + "': " + e.getMessage());
                }
            }
        }
    }

    public synchronized List<SiteView> list() {
        List<SiteView> out = new ArrayList<>();
        for (Site s : sites.values()) {
            out.add(view(s));
        }
        return out;
    }

    public synchronized String listJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Site s : sites.values()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(view(s).toJson());
        }
        return sb.append("]").toString();
    }

    /**
     * Cay file web cua site, dang JSON. Chi lay file ma site phuc vu duoc (StaticFileHandler.contentTypeFor);
     * thu muc truoc, file sau, sap xep theo ten; thu muc khong co file web nao thi bo qua. Chi doc, khong sua file.
     */
    public synchronized String filesJson(String name) throws SiteException {
        Site s = require(name);
        try {
            return "[" + String.join(",", listWebFiles(folderOf(s), "")) + "]";
        } catch (IOException e) {
            throw new SiteException(500, "Khong doc duoc thu muc cua site: " + e.getMessage());
        }
    }

    /** Cac node JSON cua mot thu muc; prefix la duong dan tuong doi cua thu muc do (dung '/'). */
    private static List<String> listWebFiles(Path dir, String prefix) throws IOException {
        List<String> nodes = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return nodes;
        }
        List<Path> entries;
        try (Stream<Path> stream = Files.list(dir)) {
            entries = stream.sorted(Comparator
                    .comparing((Path p) -> !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                    .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        }
        for (Path p : entries) {
            String fileName = p.getFileName().toString();
            String rel = prefix.isEmpty() ? fileName : prefix + "/" + fileName;
            if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                List<String> children = listWebFiles(p, rel);
                if (!children.isEmpty()) {
                    nodes.add("{\"name\":\"" + jsonEscape(fileName) + "\",\"path\":\"" + jsonEscape(rel)
                        + "\",\"dir\":true,\"children\":[" + String.join(",", children) + "]}");
                }
            } else if (StaticFileHandler.contentTypeFor(fileName) != null) {
                nodes.add("{\"name\":\"" + jsonEscape(fileName) + "\",\"path\":\"" + jsonEscape(rel) + "\",\"dir\":false}");
            }
        }
        return nodes;
    }

    /** Escape chuoi cho JSON: dau nhay kep, dau gach cheo nguoc va ky tu dieu khien. */
    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public synchronized SiteView get(String name) throws SiteException {
        return view(require(name));
    }

    public synchronized SiteView add(String name, int port) throws SiteException {
        if (!isValidName(name)) {
            throw new SiteException(400, "Ten site khong hop le: chi dung a-z, 0-9, dau gach ngang, toi da 32 ky tu, bat dau bang chu hoac so");
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new SiteException(400, "Cong phai nam trong khoang " + MIN_PORT + "-" + MAX_PORT);
        }
        if (sites.containsKey(name)) {
            throw new SiteException(409, "Site '" + name + "' da ton tai");
        }
        if (port == reservedPort || portTaken(port)) {
            throw new SiteException(409, "Cong " + port + " da duoc server chinh hoac site khac su dung");
        }
        Site s = new Site(name, port, true);
        startSite(s);
        try {
            prepareFolder(s);
        } catch (IOException e) {
            stopSite(s);
            throw new SiteException(500, "Khong tao duoc thu muc cua site: " + e.getMessage());
        }
        sites.put(name, s);
        try {
            save();
        } catch (SiteException e) {
            stopSite(s);
            sites.remove(name);
            throw e;
        }
        return view(s);
    }

    public synchronized SiteView start(String name) throws SiteException {
        Site s = require(name);
        if (s.server == null) {
            startSite(s);
        }
        s.enabled = true;
        save();
        return view(s);
    }

    public synchronized SiteView stop(String name) throws SiteException {
        Site s = require(name);
        stopSite(s);
        s.enabled = false;
        save();
        return view(s);
    }

    /** Xoa site khoi danh sach va dong cong. Thu muc file cua site duoc giu nguyen. */
    public synchronized void remove(String name) throws SiteException {
        Site s = require(name);
        stopSite(s);
        sites.remove(name);
        save();
    }

    /** Dong moi cong dang mo, khong sua sites.json (dung khi don dep / tat chuong trinh). */
    public synchronized void stopAll() {
        for (Site s : sites.values()) {
            stopSite(s);
        }
    }

    private Site require(String name) throws SiteException {
        Site s = name == null ? null : sites.get(name);
        if (s == null) {
            throw new SiteException(404, "Khong co site '" + name + "'");
        }
        return s;
    }

    private boolean portTaken(int port) {
        for (Site s : sites.values()) {
            if (s.port == port) {
                return true;
            }
        }
        return false;
    }

    private SiteView view(Site s) {
        return new SiteView(s.name, s.port, s.enabled, s.server != null);
    }

    private Path folderOf(Site s) {
        return sitesRoot.resolve(s.name);
    }

    private void prepareFolder(Site s) throws IOException {
        Path dir = folderOf(s);
        Files.createDirectories(dir);
        Path index = dir.resolve("index.html");
        if (!Files.exists(index)) {
            String html = "<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>" + s.name + "</title></head>\n"
                + "<body><h1>Site " + s.name + "</h1><p>Dang chay tai cong " + s.port
                + ". Sua file index.html trong thu muc cua site de doi noi dung.</p></body></html>\n";
            Files.writeString(index, html, StandardCharsets.UTF_8);
        }
    }

    private void startSite(Site s) throws SiteException {
        HttpServer srv = null;
        IOException last = null;
        // Thu lai ngan: cong vua dong (tat roi bat lai ngay) co the con tre vai chuc ms tren Windows
        for (int attempt = 0; attempt < 3 && srv == null; attempt++) {
            try {
                srv = HttpServer.create(new InetSocketAddress(s.port), 0);
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (srv == null) {
            throw new SiteException(409, "Khong mo duoc cong " + s.port + " (co the dang duoc chuong trinh khac su dung): "
                + (last != null ? last.getMessage() : "bi huy"));
        }
        srv.createContext("/", new SiteFileHandler(folderOf(s)));
        srv.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        srv.start();
        s.server = srv;
        System.out.println("[SITES] '" + s.name + "' dang chay tai http://localhost:" + s.port + "/ (thu muc " + folderOf(s) + ")");
    }

    private void stopSite(Site s) {
        if (s.server != null) {
            s.server.stop(0);
            s.server = null;
            System.out.println("[SITES] '" + s.name + "' da tat (cong " + s.port + ")");
        }
    }

    private void save() throws SiteException {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Site s : sites.values()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\n  {\"name\":\"").append(s.name).append("\",\"port\":").append(s.port)
                .append(",\"enabled\":").append(s.enabled).append("}");
        }
        sb.append(first ? "]" : "\n]").append("\n");
        try {
            Path tmp = configFile.resolveSibling(configFile.getFileName() + ".tmp");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new SiteException(500, "Khong ghi duoc " + configFile + ": " + e.getMessage());
        }
    }
}
