package dev.loomlab.benchmark;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkEngineTest {

    private final BenchmarkEngine engine = new BenchmarkEngine();

    @Test
    void virtualThreadsDominamIoboundComPoolPequeno() throws InterruptedException {
        // 50 tarefas × 50ms de I/O com pool de 10 platform threads:
        // plataforma ≈ 5 levas ≈ 250ms; virtual ≈ uma leva ≈ 50ms
        var report = engine.run(new BenchmarkEngine.BenchmarkSpec(50, 50, 10));

        assertThat(report.platform().totalMs()).isGreaterThanOrEqualTo(200);
        assertThat(report.virtual().totalMs()).isLessThan(150);
        assertThat(report.speedup()).isGreaterThan(1.5);

        // p50 do virtual ≈ ioDelay; p95/p99 coerentes
        assertThat(report.virtual().p50Ms()).isBetween(45L, 200L);
        assertThat(report.virtual().p99Ms()).isGreaterThanOrEqualTo(report.virtual().p50Ms());
    }

    @Test
    void throughputEThreadsReportadasCorretamente() throws InterruptedException {
        var report = engine.run(new BenchmarkEngine.BenchmarkSpec(20, 20, 10));

        assertThat(report.platform().threadsUsed()).isEqualTo(10);
        assertThat(report.virtual().threadsUsed()).isEqualTo(20);   // uma vthread por tarefa
        assertThat(report.virtual().throughputPerSec()).isGreaterThan(0);
        assertThat(report.platform().taskCount()).isEqualTo(20);
    }
}
