package co.inter.piggies.coordinator.orchestration;

import co.inter.piggies.coordinator.api.model.PaymentConfirmEvent;

public interface PaymentConfirmPublisher {

    void publish(PaymentConfirmEvent event);
}
