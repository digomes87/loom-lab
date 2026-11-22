package dev.loomlab.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteAnalyzerTest {

    private static Quote quote(String supplier, String amount) {
        return new Quote(supplier, new BigDecimal(amount), LocalTime.of(9, 0), Duration.ofHours(2));
    }

    @Test
    void sumarizaComPatternMatchingSobreHierarquiaSelada() {
        var results = List.<QuoteResult>of(
            new QuoteResult.Success(quote("SkyOne", "1000.00")),
            new QuoteResult.Failure("FlyFast", "indisponível"),
            new QuoteResult.Timeout("SlowJet"),
            new QuoteResult.Success(quote("AeroLuxe", "800.50"))
        );

        var summary = QuoteAnalyzer.summarize(results);

        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.successCount()).isEqualTo(2);
        assertThat(summary.failureCount()).isEqualTo(1);
        assertThat(summary.timeoutCount()).isEqualTo(1);
        assertThat(summary.failedSuppliers()).containsExactly("FlyFast");
        assertThat(summary.bestQuote().supplier()).isEqualTo("AeroLuxe");
        assertThat(summary.bestQuote().amountBrl()).isEqualByComparingTo("800.50");
    }

    @Test
    void listaVaziaNaoTemMelhorCotacao() {
        var summary = QuoteAnalyzer.summarize(List.of());

        assertThat(summary.total()).isZero();
        assertThat(summary.bestQuote()).isNull();
        assertThat(summary.failedSuppliers()).isEmpty();
    }

    @Test
    void supplierOfFuncionaEmTodasAsVariantes() {
        assertThat(QuoteResult.supplierOf(new QuoteResult.Success(quote("SkyOne", "100"))))
            .isEqualTo("SkyOne");
        assertThat(QuoteResult.supplierOf(new QuoteResult.Failure("FlyFast", "erro")))
            .isEqualTo("FlyFast");
        assertThat(QuoteResult.supplierOf(new QuoteResult.Timeout("SlowJet")))
            .isEqualTo("SlowJet");
    }
}
