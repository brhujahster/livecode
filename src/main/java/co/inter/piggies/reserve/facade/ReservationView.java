package co.inter.piggies.reserve.facade;

import java.time.Instant;
import java.util.UUID;

public record ReservationView(UUID paymentId, String agency, String accountNumber, long amount,
                              ReservationStatus status, Instant createdAt, Instant updatedAt) {
}
