package co.inter.piggies.reserve.web;

import co.inter.piggies.reserve.facade.ReservationStatus;
import co.inter.piggies.reserve.facade.ReservationView;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record ReservationResponse(UUID paymentId, String agency, String accountNumber, long amount,
                                  ReservationStatus status, Instant createdAt, Instant updatedAt) {

    static ReservationResponse from(ReservationView view) {
        return new ReservationResponse(view.paymentId(), view.agency(), view.accountNumber(), view.amount(),
                view.status(), view.createdAt(), view.updatedAt());
    }
}
