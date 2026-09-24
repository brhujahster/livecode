package co.inter.piggies.merchant.infra.messaging;

import co.inter.piggies.merchant.facade.CreditCommand;
import co.inter.piggies.merchant.facade.MerchantFacade;
import co.inter.piggies.merchant.facade.ReceivableView;
import io.micronaut.configuration.kafka.annotation.ErrorStrategy;
import io.micronaut.configuration.kafka.annotation.ErrorStrategyValue;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetReset;
import io.micronaut.configuration.kafka.annotation.OffsetStrategy;
import io.micronaut.configuration.kafka.annotation.Topic;

/**
 * Credita o merchant e publica a confirmação. O offset só é confirmado depois de publicar: se a publicação falhar,
 * a mensagem volta, o crédito repetido é ignorado pela chave do recebível e a confirmação sai de novo.
 */
@KafkaListener(
        groupId = "spp-merchant",
        offsetReset = OffsetReset.EARLIEST,
        offsetStrategy = OffsetStrategy.SYNC_PER_RECORD,
        errorStrategy = @ErrorStrategy(value = ErrorStrategyValue.RETRY_EXPONENTIALLY_ON_ERROR, retryCount = 5,
                retryDelay = "200ms"))
class PaymentDebitedListener {

    private final MerchantFacade merchants;
    private final PaymentConfirmedClient confirmations;

    PaymentDebitedListener(MerchantFacade merchants, PaymentConfirmedClient confirmations) {
        this.merchants = merchants;
        this.confirmations = confirmations;
    }

    @Topic("${spp.topics.payment-debited}")
    void onDebited(PaymentDebited event) {
        ReceivableView receivable = merchants.credit(
                new CreditCommand(event.paymentId(), event.merchantCnpj(), event.amount()));
        confirmations.send(receivable.paymentId().toString(), PaymentConfirmed.of(receivable));
    }
}
