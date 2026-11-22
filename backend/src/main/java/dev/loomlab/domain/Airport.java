package dev.loomlab.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Aeroporto identificado pelo código IATA (ex.: GRU, LIS).
 *
 * <p>Um {@code record} substitui, em uma linha de cabeçalho, o que antes exigia
 * constructor, getters, equals, hashCode e toString (ou Lombok). O
 * <em>compact constructor</em> abaixo roda antes da atribuição automática dos
 * campos — é o lugar certo para validação/normalização.</p>
 */
public record Airport(String code) {

    private static final Pattern IATA = Pattern.compile("[A-Z]{3}");

    public Airport {
        var normalized = code.strip().toUpperCase(Locale.ROOT);
        if (!IATA.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                "'%s' não é um código IATA válido (3 letras)".formatted(code));
        }
        code = normalized;
    }

    public static Airport of(String code) {
        return new Airport(code);
    }
}
