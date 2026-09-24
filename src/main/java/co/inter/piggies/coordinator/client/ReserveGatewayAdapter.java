package co.inter.piggies.coordinator.client;

import co.inter.piggies.coordinator.client.reserve.ReservationsApi;
import co.inter.piggies.coordinator.client.reserve.model.CreateReservationRequest;
import co.inter.piggies.coordinator.client.reserve.model.Reservation;
import co.inter.piggies.coordinator.domain.FailureReasons;
import co.inter.piggies.coordinator.domain.PaymentIntentEntity;
import co.inter.piggies.coordinator.orchestration.ReserveGateway;
import co.inter.piggies.coordinator.orchestration.ReserveOutcome;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class ReserveGatewayAdapter implements ReserveGateway {

    private final ReservationsApi reservations;

    public ReserveGatewayAdapter(ReservationsApi reservations) {
        this.reservations = reservations;
    }

    @Override
    public ReserveOutcome reserve(PaymentIntentEntity intent) {
        var request = new CreateReservationRequest(
                intent.getId(),
                intent.getPayerCpf(),
                intent.getPayerAgency(),
                intent.getPayerAccount(),
                intent.getAmount()
        );
        try {
            HttpResponse<Reservation> response = reservations.createReservation(request);
            Reservation body = response.getBody().orElse(null);
            if (body == null || body.getId() == null) {
                return ReserveOutcome.rejected(FailureReasons.ORCHESTRATION_FAILED);
            }
            return ReserveOutcome.reserved(body.getId());
        } catch (HttpClientResponseException exception) {
            if (DownstreamErrors.isRejection(exception.getStatus())) {
                return ReserveOutcome.rejected(DownstreamErrors.detail(exception, DownstreamErrors.reserveFallback(exception.getStatus())));
            }
            throw exception;
        }
    }

    @Override
    public void confirm(UUID reservationId) {
        reservations.confirmReservation(reservationId);
    }

    @Override
    public void release(UUID reservationId) {
        reservations.releaseReservation(reservationId);
    }
}
