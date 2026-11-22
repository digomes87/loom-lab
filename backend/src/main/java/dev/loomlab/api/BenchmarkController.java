package dev.loomlab.api;

import dev.loomlab.benchmark.BenchmarkEngine;
import dev.loomlab.run.BenchmarkRunEntity;
import dev.loomlab.run.BenchmarkRunRepository;
import dev.loomlab.run.RunSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BenchmarkController {

    private final BenchmarkEngine engine;
    private final BenchmarkRunRepository repository;

    public BenchmarkController(BenchmarkEngine engine, BenchmarkRunRepository repository) {
        this.engine = engine;
        this.repository = repository;
    }

    /** Corpo do POST /api/benchmark — record + Bean Validation. */
    public record BenchmarkBody(
        @Min(10) @Max(20_000) int taskCount,
        @Min(10) @Max(5_000) int ioDelayMs,
        @Min(10) @Max(1_000) int platformPoolSize
    ) {
    }

    /** Roda as duas fases e devolve o comparativo (também persistido no Postgres). */
    public record BenchmarkResponse(long runId, BenchmarkEngine.ComparisonReport report) {
    }

    @PostMapping("/benchmark")
    public BenchmarkResponse run(@Valid @RequestBody BenchmarkBody body) throws InterruptedException {
        var report = engine.run(new BenchmarkEngine.BenchmarkSpec(
            body.taskCount(), body.ioDelayMs(), body.platformPoolSize()));

        var saved = repository.save(new BenchmarkRunEntity(
            body.taskCount(), body.ioDelayMs(), body.platformPoolSize(),
            report.platform().totalMs(), report.platform().p95Ms(), report.platform().throughputPerSec(),
            report.virtual().totalMs(), report.virtual().p95Ms(), report.virtual().throughputPerSec(),
            report.speedup()));

        return new BenchmarkResponse(saved.getId(), report);
    }

    @GetMapping("/runs")
    public List<RunSummary> runs() {
        return repository.findTop50ByOrderByRanAtDesc().stream()
            .map(RunSummary::from)
            .toList();
    }
}
