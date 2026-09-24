package co.inter.piggies.coordinator.support;

import co.inter.piggies.coordinator.api.model.PaymentConfirmEvent;
import co.inter.piggies.coordinator.client.MerchantGatewayAdapter;
import co.inter.piggies.coordinator.client.ReserveGatewayAdapter;
import co.inter.piggies.coordinator.domain.PaymentIntentEntity;
import co.inter.piggies.coordinator.messaging.KafkaPaymentConfirmPublisher;
import co.inter.piggies.coordinator.orchestration.MerchantGateway;
import co.inter.piggies.coordinator.orchestration.PaymentConfirmPublisher;
import co.inter.piggies.coordinator.orchestration.ReserveGateway;
import co.inter.piggies.coordinator.orchestration.ReserveOutcome;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.UUID;

@Factory
@Requires(env = "coordinator-it")
public class CoordinatorTestDoubles {

    @Singleton
    @Replaces(ReserveGatewayAdapter.class)
    public ReserveGateway reserveGateway() {
        return new ReserveGateway() {
            @Override
            public ReserveOutcome reserve(PaymentIntentEntity intent) {
                return CoordinatorScripts.reserve.reserve(intent);
            }

            @Override
            public void confirm(UUID reservationId) {
                CoordinatorScripts.reserve.confirms.incrementAndGet();
            }

            @Override
            public void release(UUID reservationId) {
                CoordinatorScripts.reserve.releases.incrementAndGet();
            }
        };
    }

    @Singleton
    @Replaces(MerchantGatewayAdapter.class)
    public MerchantGateway merchantGateway() {
        return CoordinatorScripts.merchant::validate;
    }

    @Singleton
    @Replaces(KafkaPaymentConfirmPublisher.class)
    public PaymentConfirmPublisher paymentConfirmPublisher() {
        return (PaymentConfirmEvent event) -> CoordinatorScripts.published.incrementAndGet();
    }
}
