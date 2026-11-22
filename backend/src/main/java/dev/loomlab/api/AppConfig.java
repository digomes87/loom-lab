package dev.loomlab.api;

import dev.loomlab.supplier.FlightSupplier;
import dev.loomlab.supplier.SimulatedSuppliers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.random.RandomGenerator;

@Configuration
public class AppConfig {

    @Bean
    public RandomGenerator randomGenerator() {
        return RandomGenerator.getDefault();
    }

    /** O "mercado" de fornecedores simulados, injetado no controller. */
    @Bean
    public List<FlightSupplier> flightSuppliers(RandomGenerator rng) {
        return SimulatedSuppliers.market(rng);
    }
}
