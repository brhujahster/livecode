package co.inter.piggies.kafka;

import co.inter.piggies.event.PaymentConfirmEvent;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.configuration.kafka.annotation.KafkaKey;

import java.util.UUID;

@KafkaClient
public interface PaymentConfirmProducer {

    @Topic("spp.payment.confirm")
    void sendPaymentConfirm(@KafkaKey UUID paymentId, PaymentConfirmEvent event);
}
