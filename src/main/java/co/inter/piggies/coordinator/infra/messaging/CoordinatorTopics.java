package co.inter.piggies.coordinator.infra.messaging;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.Optional;

/**
 * Cada módulo cria o tópico que publica. Partições e réplicas seguem o padrão do broker.
 */
@Factory
class CoordinatorTopics {

    @Bean
    NewTopic paymentDebitedTopic(@Value("${spp.topics.payment-debited}") String name) {
        return new NewTopic(name, Optional.empty(), Optional.empty());
    }
}
