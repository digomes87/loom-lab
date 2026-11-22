package dev.loomlab.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.stream.Gatherer;
import java.util.stream.Gatherers;

/**
 * Pós-processamento de cotações com <em>Stream Gatherers</em> (JEP 485,
 * final no JDK 24) — operações intermediárias customizadas que antes
 * exigiam coletar a lista inteira na mão.
 */
public final class TopQuotes {

    private TopQuotes() {
    }

    /**
     * Gatherer customizado: acumula as cotações em um min-heap durante o stream
     * e emite apenas as {@code limit} mais baratas no final — em uma passada só.
     *
     * <p>Uso: {@code quotes.stream().gather(TopQuotes.cheapest(3)).toList()}</p>
     */
    public static Gatherer<Quote, ?, Quote> cheapest(int limit) {
        // os três parâmetros de tipo de Gatherer: <T elemento, A estado, R resultado>
        return Gatherer.<Quote, PriorityQueue<Quote>, Quote>ofSequential(
            () -> new PriorityQueue<>(Comparator.comparing(Quote::amountBrl)),
            (heap, quote, downstream) -> {
                heap.offer(quote);   // só acumula; quem emite é o finisher
                return true;
            },
            (heap, downstream) -> {
                for (int i = 0; i < limit && !heap.isEmpty(); i++) {
                    downstream.push(heap.poll());
                }
            }
        );
    }

    /**
     * Gatherer <em>nativo</em> do JDK ({@code Gatherers.windowSliding}): média
     * móvel dos preços em janelas consecutivas.
     */
    public static List<BigDecimal> movingAverages(List<Quote> quotes, int windowSize) {
        return quotes.stream()
            .map(Quote::amountBrl)
            .gather(Gatherers.windowSliding(windowSize))
            .map(window -> window.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(window.size()), 2, RoundingMode.HALF_UP))
            .toList();
    }
}
