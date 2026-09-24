package co.inter.piggies.coordinator.messaging;

import co.inter.piggies.coordinator.api.model.PaymentConfirmEvent;
import co.inter.piggies.coordinator.orchestration.PaymentConfirmPublisher;
import jakarta.inject.Singleton;

@Singleton
public class KafkaPaymentConfirmPublisher implements PaymentConfirmPublisher {

    private final PaymentConfirmKafkaClient client;

    public KafkaPaymentConfirmPublisher(PaymentConfirmKafkaClient client) {
        this.client = client;
    }

    @Override
    public void publish(PaymentConfirmEvent event) {
        client.publish(event);
    }
}
