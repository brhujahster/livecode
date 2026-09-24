package co.inter.piggies.merchant.infra.messaging;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.Optional;

/**
 * Cada módulo cria o tópico que publica. Partições e réplicas seguem o padrão do broker.
 */
@Factory
class MerchantTopics {

    @Bean
    NewTopic paymentConfirmedTopic(@Value("${spp.topics.payment-confirmed}") String name) {
        return new NewTopic(name, Optional.empty(), Optional.empty());
    }
}
