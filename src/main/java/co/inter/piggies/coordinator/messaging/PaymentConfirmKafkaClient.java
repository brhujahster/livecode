package co.inter.piggies.coordinator.messaging;

import co.inter.piggies.coordinator.api.model.PaymentConfirmEvent;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.Topic;

@KafkaClient
public interface PaymentConfirmKafkaClient {

    @Topic("spp.payment.confirm")
    void publish(PaymentConfirmEvent event);
}
