package dev.loomlab.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;

/**
 * Cotação de um fornecedor: o "payload de sucesso" do domínio.
 */
public record Quote(
    String supplier,
    BigDecimal amountBrl,
    LocalTime departure,
    Duration duration
) {
    public Quote {
        if (amountBrl.signum() <= 0) {
            throw new IllegalArgumentException("valor da cotação deve ser positivo");
        }
    }
}
