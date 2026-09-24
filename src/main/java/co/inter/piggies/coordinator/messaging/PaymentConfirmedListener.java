package co.inter.piggies.coordinator.messaging;

import co.inter.piggies.coordinator.api.model.PaymentConfirmedEvent;
import co.inter.piggies.coordinator.orchestration.PaymentOrchestrator;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.Topic;
import jakarta.inject.Singleton;

@Singleton
@KafkaListener(groupId = "piggies-payment-coordinator")
public class PaymentConfirmedListener {

    private final PaymentOrchestrator orchestrator;

    public PaymentConfirmedListener(PaymentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Topic("spp.payment.confirmed")
    public void onConfirmed(PaymentConfirmedEvent event) {
        orchestrator.onCreditConfirmed(event);
    }
}
