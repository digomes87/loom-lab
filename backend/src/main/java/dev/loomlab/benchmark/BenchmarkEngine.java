package dev.loomlab.benchmark;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * O laboratório: o MESMO workload de I/O bloqueante ({@code Thread.sleep})
 * executado sob dois modelos de threading:
 *
 * <ul>
 *   <li><b>PLATFORM</b> — pool fixo de {@code platformPoolSize} threads de
 *       plataforma (o modelo clássico "uma thread por request até acabar o pool");</li>
 *   <li><b>VIRTUAL</b> — uma virtual thread por tarefa
 *       ({@code Executors.newVirtualThreadPerTaskExecutor()}, JEP 444).</li>
 * </ul>
 *
 * <p>Com pool = 100, tarefas = 1000 e I/O = 100ms, o pool de plataforma precisa
 * de 10 "levas" (≈1s); as virtual threads fazem todas de uma vez (≈100ms).
 * A diferença — visível no dashboard — não é mágica: é a thread deixar de ser
 * um recurso escasso do SO.</p>
 */
@Component
public class BenchmarkEngine {

    /** Parâmetros da rodada. */
    public record BenchmarkSpec(int taskCount, int ioDelayMs, int platformPoolSize) {
    }

    /** Resultado de uma fase (um modelo de threading). */
    public record PhaseResult(
        String threadModel,
        int taskCount,
        int threadsUsed,
        long totalMs,
        double throughputPerSec,
        long p50Ms,
        long p95Ms,
        long p99Ms
    ) {
    }

    /** Relatório comparativo completo — o que o dashboard plota. */
    public record ComparisonReport(
        BenchmarkSpec spec,
        PhaseResult platform,
        PhaseResult virtual,
        double speedup
    ) {
    }

    public ComparisonReport run(BenchmarkSpec spec) throws InterruptedException {
        var platform = runPhase("PLATFORM", spec,
            Executors.newFixedThreadPool(spec.platformPoolSize()));
        var virtual = runPhase("VIRTUAL", spec,
            Executors.newVirtualThreadPerTaskExecutor());

        return new ComparisonReport(spec, platform, virtual,
            (double) platform.totalMs() / virtual.totalMs());
    }

    private PhaseResult runPhase(String model, BenchmarkSpec spec, ExecutorService executor)
            throws InterruptedException {

        var latenciesMs = new AtomicLongArray(spec.taskCount());
        var done = new CountDownLatch(spec.taskCount());
        var start = System.nanoTime();

        try (executor) {   // ExecutorService é AutoCloseable desde o JDK 19: close() espera as tarefas
            for (int i = 0; i < spec.taskCount(); i++) {
                final var index = i;
                executor.submit(() -> {
                    var taskStart = System.nanoTime();
                    Thread.sleep(spec.ioDelayMs());   // I/O bloqueante simulado
                    latenciesMs.set(index, (System.nanoTime() - taskStart) / 1_000_000);
                    done.countDown();
                    return null;
                });
            }
            done.await();
        }

        var totalMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        var sorted = new long[spec.taskCount()];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = latenciesMs.get(i);
        }
        Arrays.sort(sorted);

        return new PhaseResult(
            model,
            spec.taskCount(),
            "PLATFORM".equals(model) ? spec.platformPoolSize() : spec.taskCount(),
            totalMs,
            spec.taskCount() / (totalMs / 1000.0),
            percentile(sorted, 0.50),
            percentile(sorted, 0.95),
            percentile(sorted, 0.99)
        );
    }

    private static long percentile(long[] sorted, double q) {
        var index = (int) Math.ceil(q * sorted.length);
        return sorted[Math.min(sorted.length, Math.max(1, index)) - 1];
    }
}
