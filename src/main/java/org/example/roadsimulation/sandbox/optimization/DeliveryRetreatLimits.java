package org.example.roadsimulation.sandbox.optimization;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Eligibility for the best archive, independently of the four-objective score. */
public record DeliveryRetreatLimits(BigDecimal cumulative, BigDecimal terminal) {
    public DeliveryRetreatLimits {
        cumulative = ratio(cumulative);
        terminal = ratio(terminal);
    }

    public List<String> issues(FulfillmentComparison.Side baseline, FulfillmentComparison.Side candidate) {
        if (baseline == null || candidate == null) return List.of("DELIVERY_EVIDENCE_MISSING");
        var issues = new ArrayList<String>();
        if (exceeds(baseline.deliveredTonnes(), candidate.deliveredTonnes(), cumulative))
            issues.add("CUMULATIVE_DELIVERY_RETREAT_EXCEEDED");
        if (exceeds(baseline.terminalDeliveredTonnes(), candidate.terminalDeliveredTonnes(), terminal))
            issues.add("TERMINAL_DELIVERY_RETREAT_EXCEEDED");
        return List.copyOf(issues);
    }

    private static boolean exceeds(BigDecimal baseline, BigDecimal candidate, BigDecimal limit) {
        return baseline.subtract(candidate).compareTo(baseline.multiply(limit)) > 0;
    }

    private static BigDecimal ratio(BigDecimal value) {
        value = ContractChecks.nonNegative(value, "delivery retreat limit");
        if (value.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException("Delivery retreat limit exceeds 1");
        return value;
    }
}
