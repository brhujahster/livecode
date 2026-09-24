package co.inter.piggies.coordinator.orchestration;

import java.util.UUID;

public record ReserveOutcome(UUID reservationId, String failureReason, boolean succeeded) {

    public static ReserveOutcome reserved(UUID reservationId) {
        return new ReserveOutcome(reservationId, null, true);
    }

    public static ReserveOutcome rejected(String failureReason) {
        return new ReserveOutcome(null, failureReason, false);
    }
}
