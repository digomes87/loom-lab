package dev.loomlab.api;

// Boot 4 usa Jackson 3 (tools.jackson.*), não mais com.fasterxml.*
import tools.jackson.databind.JsonNode;
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

/**
 * Teste de ponta a ponta com Tomcat REAL (porta aleatória) — por isso consegue
 * provar que o container roda os requests em virtual threads.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> post(String json) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:%d/api/search".formatted(port)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void buscaFazFanOutEstruturadoComOsCincoFornecedores() throws Exception {
        var response = post("{\"origin\":\"GRU\",\"destination\":\"LIS\"}");
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.path("results")).hasSize(5);

        // SlowJet (1100–1500ms) sempre estoura o orçamento de 900ms
        assertThat(body.path("summary").path("timeoutCount").asInt()).isGreaterThanOrEqualTo(1);

        // A PROVA: o request HTTP inteiro rodou numa virtual thread do Tomcat
        assertThat(body.path("handlerThread").path("virtual").asBoolean()).isTrue();
        assertThat(body.path("handlerThread").path("name").asText()).contains("VirtualThread");
    }

    @Test
    void origemIgualAoDestinoDevolve400() throws Exception {
        var response = post("{\"origin\":\"GRU\",\"destination\":\"gru\"}");
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void corpoInvalidoDevolve400() throws Exception {
        var response = post("{\"origin\":\"\",\"destination\":\"LIS\"}");
        assertThat(response.statusCode()).isEqualTo(400);
    }
}
