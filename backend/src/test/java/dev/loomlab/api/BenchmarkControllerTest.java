package dev.loomlab.api;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BenchmarkControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:%d%s".formatted(port, path)))
            .header("Content-Type", "application/json");
        builder = switch (method) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body));
            default -> builder.GET();
        };
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void benchmarkComparaOsDoisModelosEPersiste() throws Exception {
        var response = send("POST", "/api/benchmark",
            """
            {"taskCount": 60, "ioDelayMs": 40, "platformPoolSize": 10}
            """);   // text block (JEP 378): JSON legível dentro do Java
        var body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.path("runId").asLong()).isPositive();
        assertThat(body.path("report").path("platform").path("threadModel").asText()).isEqualTo("PLATFORM");
        assertThat(body.path("report").path("virtual").path("threadModel").asText()).isEqualTo("VIRTUAL");
        assertThat(body.path("report").path("speedup").asDouble()).isGreaterThan(1.0);

        // o histórico mostra a rodada recém-executada
        var runs = objectMapper.readTree(send("GET", "/api/runs", null).body());
        assertThat(runs.isArray()).isTrue();
        assertThat(runs.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void specInvalidaDevolve400() throws Exception {
        var response = send("POST", "/api/benchmark",
            """
            {"taskCount": 1, "ioDelayMs": 40, "platformPoolSize": 10}
            """);
        assertThat(response.statusCode()).isEqualTo(400);
    }
}
