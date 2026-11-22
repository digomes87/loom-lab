package dev.loomlab.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TopQuotesTest {

    private static Quote quote(String supplier, String amount) {
        return new Quote(supplier, new BigDecimal(amount), LocalTime.of(9, 0), Duration.ofHours(2));
    }

    @Test
    void gathererCustomizadoDevolveAsKCotacoesMaisBaratasEmOrdem() {
        var quotes = List.of(
            quote("A", "900"),
            quote("B", "700"),
            quote("C", "800"),
            quote("D", "600"),
            quote("E", "1000")
        );

        var cheapest3 = quotes.stream().gather(TopQuotes.cheapest(3)).toList();

        assertThat(cheapest3).extracting(Quote::supplier).containsExactly("D", "B", "C");
    }

    @Test
    void gathererNativoCalculaMediaMovelDePrecos() {
        var quotes = List.of(
            quote("A", "100"),
            quote("B", "200"),
            quote("C", "300")
        );

        var averages = TopQuotes.movingAverages(quotes, 2);

        assertThat(averages).hasSize(2);
        assertThat(averages.get(0)).isEqualByComparingTo("150");
        assertThat(averages.get(1)).isEqualByComparingTo("250");
        assertThat(averages.get(0).doubleValue()).isCloseTo(150.0, within(0.001));
    }
}
