package co.inter.piggies.coordinator.infra.messaging;

import co.inter.piggies.coordinator.domain.PaymentEventPublisher;
import co.inter.piggies.coordinator.domain.PaymentIntent;
import jakarta.inject.Singleton;

@Singleton
class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final PaymentDebitedClient client;

    KafkaPaymentEventPublisher(PaymentDebitedClient client) {
        this.client = client;
    }

    @Override
    public void debited(PaymentIntent debited) {
        client.send(debited.getId().toString(), PaymentDebited.of(debited));
    }
}
