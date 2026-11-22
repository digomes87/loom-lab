package dev.loomlab.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRunEntity, Long> {

    /** Derivação por nome de método: sem JPQL, sem implementação. */
    List<BenchmarkRunEntity> findTop50ByOrderByRanAtDesc();
}
