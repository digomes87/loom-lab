package dev.loomlab.supplier;

import dev.loomlab.domain.Quote;
import dev.loomlab.domain.SearchRequest;

/**
 * Contrato de um fornecedor de passagens (simulado ou real).
 *
 * <p>Uma interface funcional-style com dois métodos, mas {@link #name()} é
 * trivial — o importante é {@link #fetch}, que é <em>bloqueante</em> de propósito:
 * é exatamente o tipo de chamada que sofria com pools de threads de plataforma
 * e que as virtual threads tornam barata.</p>
 */
public interface FlightSupplier {

    String name();

    /**
     * Consulta a cotação — simula I/O bloqueante (rede + processamento lento).
     *
     * @throws InterruptedException se a thread for interrompida (cancelamento estruturado)
     * @throws RuntimeException     falha de negócio do fornecedor
     */
    Quote fetch(SearchRequest request) throws InterruptedException;
}
