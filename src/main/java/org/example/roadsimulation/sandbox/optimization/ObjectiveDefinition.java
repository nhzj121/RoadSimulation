package org.example.roadsimulation.sandbox.optimization;

import java.math.BigDecimal;
import java.util.Objects;

public record ObjectiveDefinition(String metricId, String unit, Direction direction,
                                  BigDecimal minimum, BigDecimal maximum) {
    public enum Direction { MINIMIZE, MAXIMIZE }

    public ObjectiveDefinition {
        metricId = ContractChecks.text(metricId, "metricId");
        unit = ContractChecks.text(unit, "unit");
        Objects.requireNonNull(direction, "direction");
        minimum = minimum == null ? null : ContractChecks.decimal(minimum, "minimum");
        maximum = maximum == null ? null : ContractChecks.decimal(maximum, "maximum");
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0)
            throw new IllegalArgumentException("Invalid range: " + metricId);
    }

    public void validate(BigDecimal value) {
        ContractChecks.decimal(value, metricId);
        if ((minimum != null && value.compareTo(minimum) < 0)
                || (maximum != null && value.compareTo(maximum) > 0))
            throw new IllegalArgumentException("Out of range: " + metricId);
    }
}
