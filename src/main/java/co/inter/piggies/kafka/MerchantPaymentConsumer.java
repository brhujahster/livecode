package co.inter.piggies.kafka;

import co.inter.piggies.dto.CreateCreditRequest;
import co.inter.piggies.event.PaymentConfirmEvent;
import co.inter.piggies.event.PaymentConfirmedEvent;
import co.inter.piggies.model.Receivable;
import co.inter.piggies.service.PiggiesMerchantService;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetReset;
import io.micronaut.configuration.kafka.annotation.Topic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@KafkaListener(offsetReset = OffsetReset.EARLIEST, groupId = "merchant-group")
@RequiredArgsConstructor
public class MerchantPaymentConsumer {

    private final PiggiesMerchantService merchantService;
    private final PaymentConfirmedProducer paymentConfirmedProducer;

    @Topic("spp.payment.confirm")
    public void onPaymentConfirm(PaymentConfirmEvent event) {
        log.info("Recebido evento spp.payment.confirm para paymentId: {}", event.getPaymentId());
        CreateCreditRequest request = CreateCreditRequest.builder()
                .paymentIntentId(event.getPaymentId())
                .merchantCnpj(event.getMerchantCnpj())
                .amount(event.getAmount())
                .build();

        Receivable receivable = merchantService.createCredit(request);

        PaymentConfirmedEvent confirmedEvent = PaymentConfirmedEvent.builder()
                .paymentId(receivable.getPaymentIntentId())
                .merchantCnpj(event.getMerchantCnpj())
                .amount(receivable.getAmount())
                .creditedAt(receivable.getCreditedAt())
                .build();

        log.info("Publicando evento spp.payment.confirmed para paymentId: {}", confirmedEvent.getPaymentId());
        paymentConfirmedProducer.sendPaymentConfirmed(confirmedEvent.getPaymentId(), confirmedEvent);
    }
}
