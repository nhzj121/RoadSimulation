package org.example.roadsimulation.sandbox.optimization;

import java.math.BigDecimal;
import java.util.*;

final class ContractChecks {
    private ContractChecks() {}

    static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    static String hash(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256");
        return value;
    }

    static String version(String actual, String expected) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("Unsupported artifactVersion: " + actual);
        return actual;
    }

    static BigDecimal decimal(BigDecimal value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }

    static BigDecimal positive(BigDecimal value, String field) {
        value = decimal(value, field);
        if (value.signum() <= 0) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    static BigDecimal nonNegative(BigDecimal value, String field) {
        value = decimal(value, field);
        if (value.signum() < 0) throw new IllegalArgumentException(field + " must be non-negative");
        return value;
    }

    static <T> List<T> list(Collection<T> values, String field) {
        if (values == null || values.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException(field + " must not contain nulls");
        return List.copyOf(values);
    }

    static Map<String, BigDecimal> decimals(Map<String, BigDecimal> values, String field) {
        if (values == null) throw new IllegalArgumentException(field + " is required");
        var copy = new TreeMap<String, BigDecimal>();
        values.forEach((key, value) -> copy.put(text(key, field + " key"), decimal(value, field + "." + key)));
        return Collections.unmodifiableMap(copy);
    }
}
