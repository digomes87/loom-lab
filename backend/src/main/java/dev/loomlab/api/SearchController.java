package dev.loomlab.api;

import dev.loomlab.aggregator.QuoteAggregator;
import dev.loomlab.domain.SearchRequest;
import dev.loomlab.supplier.FlightSupplier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final QuoteAggregator aggregator;
    private final List<FlightSupplier> suppliers;

    public SearchController(QuoteAggregator aggregator, List<FlightSupplier> suppliers) {
        this.aggregator = aggregator;
        this.suppliers = suppliers;
    }

    /** Corpo da requisição — record com validação bean (Bean Validation 3.1). */
    public record SearchBody(@NotBlank String origin, @NotBlank String destination) {
    }

    @PostMapping
    public QuoteAggregator.AggregatedSearch search(@Valid @RequestBody SearchBody body)
            throws InterruptedException {
        return aggregator.search(UUID.randomUUID().toString().substring(0, 8),
            SearchRequest.of(body.origin(), body.destination()),
            suppliers);
    }
}
