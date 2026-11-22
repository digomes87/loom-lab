package dev.loomlab.run;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Histórico de rodadas de benchmark. Aqui um <b>record não serve</b> — entidades
 * JPA precisam de construtor vazio e campos mutáveis — e essa é a fronteira
 * honesta do que é DTO (record) vs. estado persistido (classe).
 */
@Entity
@Table(name = "benchmark_runs")
public class BenchmarkRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant ranAt;

    private int taskCount;
    private int ioDelayMs;
    private int platformPoolSize;

    private long platformTotalMs;
    private long platformP95Ms;
    private double platformThroughput;

    private long virtualTotalMs;
    private long virtualP95Ms;
    private double virtualThroughput;

    private double speedup;

    protected BenchmarkRunEntity() {   // exigência do JPA
    }

    public BenchmarkRunEntity(int taskCount, int ioDelayMs, int platformPoolSize,
                              long platformTotalMs, long platformP95Ms, double platformThroughput,
                              long virtualTotalMs, long virtualP95Ms, double virtualThroughput,
                              double speedup) {
        this.ranAt = Instant.now();
        this.taskCount = taskCount;
        this.ioDelayMs = ioDelayMs;
        this.platformPoolSize = platformPoolSize;
        this.platformTotalMs = platformTotalMs;
        this.platformP95Ms = platformP95Ms;
        this.platformThroughput = platformThroughput;
        this.virtualTotalMs = virtualTotalMs;
        this.virtualP95Ms = virtualP95Ms;
        this.virtualThroughput = virtualThroughput;
        this.speedup = speedup;
    }

    public Long getId() {
        return id;
    }

    public Instant getRanAt() {
        return ranAt;
    }

    public int getTaskCount() {
        return taskCount;
    }

    public int getIoDelayMs() {
        return ioDelayMs;
    }

    public int getPlatformPoolSize() {
        return platformPoolSize;
    }

    public long getPlatformTotalMs() {
        return platformTotalMs;
    }

    public long getPlatformP95Ms() {
        return platformP95Ms;
    }

    public double getPlatformThroughput() {
        return platformThroughput;
    }

    public long getVirtualTotalMs() {
        return virtualTotalMs;
    }

    public long getVirtualP95Ms() {
        return virtualP95Ms;
    }

    public double getVirtualThroughput() {
        return virtualThroughput;
    }

    public double getSpeedup() {
        return speedup;
    }
}
