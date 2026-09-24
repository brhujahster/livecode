package co.inter.piggies.coordinator.orchestration;

import co.inter.piggies.coordinator.domain.PaymentIntentEntity;

import java.util.UUID;

public interface ReserveGateway {

    ReserveOutcome reserve(PaymentIntentEntity intent);

    void confirm(UUID reservationId);

    void release(UUID reservationId);
}
