package org.example.roadsimulation.optimizer.multi.ga;

import org.example.roadsimulation.entity.ShipmentItem;
import org.example.roadsimulation.optimizer.multi.insertion.InsertionCandidate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class InsertionThresholdPolicy {

    private InsertionThresholdPolicy() {
    }

    public static List<InsertionCandidate> filterAcceptable(
            List<InsertionCandidate> candidates,
            ShipmentItem item,
            MutationConfig config
    ) {
        List<InsertionCandidate> accepted = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            return accepted;
        }

        for (InsertionCandidate candidate : candidates) {
            if (isAcceptable(candidate, item, config)) {
                accepted.add(candidate);
            }
        }
        return accepted;
    }

    public static boolean isAcceptable(
            InsertionCandidate candidate,
            ShipmentItem item,
            MutationConfig config
    ) {
        if (candidate == null) {
            return false;
        }

        if (config == null) {
            config = new MutationConfig();
        }

        double baseThreshold = config.getMaxInsertionScore();
        if (baseThreshold <= 0.0 || Double.isInfinite(baseThreshold)) {
            return true;
        }

        double waitingHours = estimateWaitingHours(item);
        double relaxFactor = Math.max(0.0, config.getInsertionWaitingRelaxFactor());
        double effectiveThreshold = baseThreshold * (1.0 + waitingHours * relaxFactor);

        return candidate.getScore() <= effectiveThreshold;
    }

    private static double estimateWaitingHours(ShipmentItem item) {
        if (item == null || item.getShipment() == null
                || item.getShipment().getCreatedAt() == null) {
            return 0.0;
        }

        try {
            return Math.max(
                    0.0,
                    Duration.between(
                            item.getShipment().getCreatedAt(),
                            LocalDateTime.now()
                    ).toMinutes() / 60.0
            );
        } catch (Exception ignored) {
            return 0.0;
        }
    }
}
