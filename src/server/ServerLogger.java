package server;
public class ServerLogger {
    public static void log(String method, String path, int status, int bytes, String remote) {
        System.out.println("[" + method + "] " + path);
        String label = statusLabel(status);
        System.out.println(status + " " + label + "  " + bytes + " bytes");
        System.out.println("Remote: " + remote);
        System.out.println("----------------------------------------");
    }
    private static String statusLabel(int s) {
        switch (s) {
            case 200: return "OK";
            case 201: return "Created";
            case 400: return "Bad Request";
            case 404: return "Not Found";
            case 415: return "Unsupported Media Type";
            case 502: return "Bad Gateway";
            case 500: return "Internal Server Error";
            default: return "";
        }
    }
}
