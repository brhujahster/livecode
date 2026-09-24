package co.inter.piggies.merchant.infra.messaging;

import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.Topic;

/**
 * Método síncrono: só retorna depois do ack de todas as réplicas, para o listener só confirmar o offset do
 * débito depois que a confirmação foi publicada.
 */
@KafkaClient(id = "spp-merchant", acks = KafkaClient.Acknowledge.ALL)
interface PaymentConfirmedClient {

    @Topic("${spp.topics.payment-confirmed}")
    void send(@KafkaKey String paymentId, PaymentConfirmed event);
}
