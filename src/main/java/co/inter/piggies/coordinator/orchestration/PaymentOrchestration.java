package co.inter.piggies.coordinator.orchestration;

import java.util.UUID;

public interface PaymentOrchestration {

    void start(UUID paymentId);
}
