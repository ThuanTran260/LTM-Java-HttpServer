package client;
import java.net.URI;
import java.net.http.*;
public class HttpClientGetDemo {
    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "http://localhost:8080/api/time";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("Status: " + res.statusCode());
        System.out.println("Content-Type: " + res.headers().firstValue("Content-Type").orElse(""));
        System.out.println("Body: " + res.body());
    }
}
