package dev.loomlab.domain;

import java.util.ArrayList;
import java.util.List;

// importa as variantes aninhadas de QuoteResult para os cases do switch ficarem limpos
import dev.loomlab.domain.QuoteResult.Failure;
import dev.loomlab.domain.QuoteResult.Success;
import dev.loomlab.domain.QuoteResult.Timeout;

/**
 * Agregação de resultados com <em>pattern matching for switch</em> (JEP 441,
 * final no JDK 21) sobre a hierarquia selada {@link QuoteResult}.
 *
 * <p>Repare:</p>
 * <ul>
 *   <li>não há {@code default} — como {@code QuoteResult} é {@code sealed},
 *       o compilador prova que os três casos cobrem tudo;</li>
 *   <li>{@code case Success(Quote quote)} é um <em>record pattern</em> (JEP 440):
 *       desconstrói o record e vincula a variável em um passo;</li>
 *   <li>{@code _} (JEP 456) ignora componentes que não interessam.</li>
 * </ul>
 */
public final class QuoteAnalyzer {

    private QuoteAnalyzer() {
    }

    /** Resumo de uma rodada de cotações — também um record, claro. */
    public record Summary(
        int total,
        int successCount,
        int failureCount,
        int timeoutCount,
        Quote bestQuote,
        List<String> failedSuppliers
    ) {
        public Summary {
            failedSuppliers = List.copyOf(failedSuppliers);
        }
    }

    public static Summary summarize(List<QuoteResult> results) {
        var failedSuppliers = new ArrayList<String>();
        var bestQuote = (Quote) null;
        int success = 0, failure = 0, timeout = 0;

        for (QuoteResult result : results) {
            switch (result) {
                // record pattern: desconstrói Success e vincula o Quote interno
                case Success(Quote quote) -> {
                    success++;
                    if (bestQuote == null || quote.amountBrl().compareTo(bestQuote.amountBrl()) < 0) {
                        bestQuote = quote;
                    }
                }
                // '_' descarta a razão do erro; só o nome do fornecedor interessa
                case Failure(String supplier, String _) -> {
                    failure++;
                    failedSuppliers.add(supplier);
                }
                case Timeout(String _) -> timeout++;
            }
        }

        return new Summary(results.size(), success, failure, timeout, bestQuote, failedSuppliers);
    }
}
