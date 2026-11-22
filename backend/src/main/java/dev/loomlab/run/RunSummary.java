package dev.loomlab.run;

import java.time.Instant;

/** Projeção de leitura para o histórico — DTO enxuto, um record. */
public record RunSummary(
    Long id,
    Instant ranAt,
    int taskCount,
    int ioDelayMs,
    int platformPoolSize,
    long platformTotalMs,
    long virtualTotalMs,
    double speedup
) {
    public static RunSummary from(BenchmarkRunEntity entity) {
        return new RunSummary(
            entity.getId(),
            entity.getRanAt(),
            entity.getTaskCount(),
            entity.getIoDelayMs(),
            entity.getPlatformPoolSize(),
            entity.getPlatformTotalMs(),
            entity.getVirtualTotalMs(),
            entity.getSpeedup()
        );
    }
}
