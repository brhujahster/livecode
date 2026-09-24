package co.inter.piggies.coordinator.infra.messaging;

import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.Topic;

/**
 * Método síncrono: só retorna depois do ack de todas as réplicas, para o orquestrador saber se publicou.
 */
@KafkaClient(id = "spp-coordinator", acks = KafkaClient.Acknowledge.ALL)
interface PaymentDebitedClient {

    @Topic("${spp.topics.payment-debited}")
    void send(@KafkaKey String paymentId, PaymentDebited event);
}
