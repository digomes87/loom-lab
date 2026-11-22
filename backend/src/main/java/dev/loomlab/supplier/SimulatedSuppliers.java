package dev.loomlab.supplier;

import dev.loomlab.aggregator.TraceContext;
import dev.loomlab.domain.Quote;
import dev.loomlab.domain.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Fornecedores simulados: cada um tem uma faixa de latência e uma taxa de falha.
 * O {@link Thread#sleep} é o "I/O bloqueante" — em um pool de plataforma threads
 * ele ocuparia a thread inteira; em virtual threads, a thread simplesmente
 * devolve a carrier thread ao SO durante a espera.
 */
public final class SimulatedSuppliers {

    private static final Logger log = LoggerFactory.getLogger("loomlab.supplier");

    public record Config(String name, int minLatencyMs, int maxLatencyMs, double failureRate) {
    }

    /** O "mercado": um rápido e confiável, alguns instáveis, e um cronicamente lento. */
    public static List<FlightSupplier> market(RandomGenerator rng) {
        return List.of(
            simulated(new Config("SkyOne", 100, 300, 0.00), rng),
            simulated(new Config("FlyFast", 300, 700, 0.10), rng),
            simulated(new Config("SlowJet", 1100, 1500, 0.00), rng),   // estoura o orçamento de 900ms
            simulated(new Config("AeroLuxe", 150, 500, 0.05), rng),
            simulated(new Config("BudgetAir", 200, 600, 0.15), rng)
        );
    }

    private static FlightSupplier simulated(Config cfg, RandomGenerator rng) {
        return new FlightSupplier() {

            @Override
            public String name() {
                return cfg.name();
            }

            @Override
            public Quote fetch(SearchRequest request) throws InterruptedException {
                var start = System.nanoTime();
                Thread.sleep(rng.nextInt(cfg.minLatencyMs(), cfg.maxLatencyMs() + 1));

                if (rng.nextDouble() < cfg.failureRate()) {
                    throw new IllegalStateException("%s fora do ar".formatted(cfg.name()));
                }

                var quote = new Quote(
                    cfg.name(),
                    BigDecimal.valueOf(30_000 + rng.nextInt(150_000), 2),   // R$ 300,00–1799,99
                    LocalTime.of(rng.nextInt(6, 22), rng.nextInt(4) * 15),
                    Duration.ofMinutes(60 + rng.nextInt(600))
                );

                // TraceContext lê o traceId via ScopedValue — herdado da thread-mãe,
                // sem ThreadLocal e sem parâmetro extra espalhado pela assinatura.
                log.info("[{}] {} → R$ {} em {}ms",
                    TraceContext.TRACE_ID.orElse("sem-trace"),
                    cfg.name(), quote.amountBrl(), (System.nanoTime() - start) / 1_000_000);
                return quote;
            }
        };
    }

    private SimulatedSuppliers() {
    }
}
