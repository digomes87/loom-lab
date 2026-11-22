package dev.loomlab.domain;

/**
 * Resultado da consulta a um fornecedor — o coração do domínio.
 *
 * <p>Três features modernas em um arquivo só:</p>
 * <ul>
 *   <li>{@code sealed interface} (JEP 409): fecha o conjunto de variantes —
 *       quem implementar fora da lista {@code permits} não compila;</li>
 *   <li>records aninhados implementando a interface: cada variante carrega
 *       exatamente os dados que lhe interessam;</li>
 *   <li>consequência: switches sobre {@code QuoteResult} podem dispensar
 *       {@code default} — o compilador prova a exaustividade (JEP 441).
 *       Se amanhã nascer uma variante nova, todo switch sem cobertura quebra
 *       na compilação, não em produção.</li>
 * </ul>
 */
public sealed interface QuoteResult {

    /** Fornecedor respondeu com uma cotação. */
    record Success(Quote quote) implements QuoteResult {}

    /** Fornecedor respondeu com erro. */
    record Failure(String supplier, String reason) implements QuoteResult {}

    /** Fornecedor não respondeu dentro do prazo (cancelado pela concorrência estruturada). */
    record Timeout(String supplier) implements QuoteResult {}

    /** Nome do fornecedor em qualquer variante — switch expression com pattern matching. */
    static String supplierOf(QuoteResult result) {
        return switch (result) {
            case Success(Quote quote) -> quote.supplier();
            case Failure(String supplier, String reason) -> supplier;
            case Timeout(String supplier) -> supplier;
        };
    }
}
