package client;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
public class HttpClientErrorDemo {
    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "http://localhost:8080/api/time";
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            int s = res.statusCode();
            if (s >= 200 && s < 300) System.out.println("Thanh cong: " + res.body());
            else System.out.println("HTTP Error: " + s + " Body: " + res.body());
        } catch (HttpConnectTimeoutException e) {
            System.out.println("Request bi timeout: " + e.getMessage());
        } catch (HttpTimeoutException e) {
            System.out.println("Request bi timeout: " + e.getMessage());
        } catch (ConnectException e) {
            System.out.println("Loi ket noi: " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("Bi gian doan: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.out.println("Loi: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
