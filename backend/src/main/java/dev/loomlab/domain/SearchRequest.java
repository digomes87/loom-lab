package dev.loomlab.domain;

/**
 * Pedido de busca de passagens: origem → destino.
 *
 * <p>Record com compact constructor garantindo a regra de negócio
 * "origem e destino não podem ser o mesmo aeroporto".</p>
 */
public record SearchRequest(Airport origin, Airport destination) {

    public SearchRequest {
        if (origin.equals(destination)) {
            throw new IllegalArgumentException(
                "origem e destino não podem ser o mesmo aeroporto: %s".formatted(origin));
        }
    }

    public static SearchRequest of(String origin, String destination) {
        return new SearchRequest(Airport.of(origin), Airport.of(destination));
    }
}
