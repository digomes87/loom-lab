package dev.loomlab.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AirportTest {

    @Test
    void normalizaCodigoParaMaiusculas() {
        assertThat(Airport.of("gru").code()).isEqualTo("GRU");
        assertThat(Airport.of("  lis ").code()).isEqualTo("LIS");
    }

    @Test
    void rejeitaCodigoInvalido() {
        assertThatThrownBy(() -> Airport.of("GRU1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("IATA");
    }

    @Test
    void searchRequestRejeitaOrigemIgualAoDestino() {
        assertThatThrownBy(() -> SearchRequest.of("GRU", "gru"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mesmo aeroporto");
    }

    @Test
    void recordsGeramEqualsEToStringAutomaticamente() {
        assertThat(Airport.of("GRU")).isEqualTo(Airport.of("gru"));
        assertThat(Airport.of("GRU")).hasToString("Airport[code=GRU]");
    }
}
