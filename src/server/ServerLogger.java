package server;
public class ServerLogger {
    public static synchronized void log(String method, String path, int status, int bytes, String remote) {
        String cleanRemote = (remote == null) ? "unknown" : remote;
        if (cleanRemote.contains("/")) {
            cleanRemote = cleanRemote.substring(cleanRemote.lastIndexOf('/') + 1);
        }
        String label = statusLabel(status);
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(method).append("] ").append(path).append(System.lineSeparator());
        sb.append(status).append(" ").append(label).append("  ").append(bytes).append(" bytes").append(System.lineSeparator());
        sb.append("Remote: ").append(cleanRemote).append(System.lineSeparator());
        sb.append("----------------------------------------");
        System.out.println(sb.toString());
    }
    private static String statusLabel(int s) {
        switch (s) {
            case 200: return "OK";
            case 201: return "Created";
            case 301: return "Moved Permanently";
            case 302: return "Found";
            case 400: return "Bad Request";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 409: return "Conflict";
            case 405: return "Method Not Allowed";
            case 415: return "Unsupported Media Type";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            default: return "";
        }
    }
}
