package co.inter.piggies.kafka;

import co.inter.piggies.event.PaymentConfirmedEvent;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.configuration.kafka.annotation.KafkaKey;

import java.util.UUID;

@KafkaClient
public interface PaymentConfirmedProducer {

    @Topic("spp.payment.confirmed")
    void sendPaymentConfirmed(@KafkaKey UUID paymentId, PaymentConfirmedEvent event);
}
