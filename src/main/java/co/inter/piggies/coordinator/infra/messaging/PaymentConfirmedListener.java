package co.inter.piggies.coordinator.infra.messaging;

import co.inter.piggies.coordinator.domain.PaymentService;
import io.micronaut.configuration.kafka.annotation.ErrorStrategy;
import io.micronaut.configuration.kafka.annotation.ErrorStrategyValue;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetReset;
import io.micronaut.configuration.kafka.annotation.OffsetStrategy;
import io.micronaut.configuration.kafka.annotation.Topic;

/**
 * Fecha o pagamento quando o merchant confirma o crédito. O offset só é confirmado depois de gravar; em erro,
 * a mesma mensagem é tentada de novo em vez de ser pulada.
 */
@KafkaListener(
        groupId = "spp-coordinator",
        offsetReset = OffsetReset.EARLIEST,
        offsetStrategy = OffsetStrategy.SYNC_PER_RECORD,
        errorStrategy = @ErrorStrategy(value = ErrorStrategyValue.RETRY_EXPONENTIALLY_ON_ERROR, retryCount = 5,
                retryDelay = "200ms"))
class PaymentConfirmedListener {

    private final PaymentService payments;

    PaymentConfirmedListener(PaymentService payments) {
        this.payments = payments;
    }

    @Topic("${spp.topics.payment-confirmed}")
    void onConfirmed(PaymentConfirmed event) {
        payments.confirmCredited(event.paymentId());
    }
}
