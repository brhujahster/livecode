package co.inter.piggies.kafka;

import co.inter.piggies.event.PaymentConfirmedEvent;
import co.inter.piggies.model.PaymentIntent;
import co.inter.piggies.model.PaymentStatus;
import co.inter.piggies.repository.PaymentIntentRepository;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetReset;
import io.micronaut.configuration.kafka.annotation.Topic;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@KafkaListener(offsetReset = OffsetReset.EARLIEST, groupId = "coordinator-group")
@RequiredArgsConstructor
public class CoordinatorPaymentConsumer {

    private final PaymentIntentRepository paymentIntentRepository;

    @Topic("spp.payment.confirmed")
    @Transactional
    public void onPaymentConfirmed(PaymentConfirmedEvent event) {
        log.info("Recebido evento spp.payment.confirmed para paymentId: {}", event.getPaymentId());
        Optional<PaymentIntent> optionalIntent = paymentIntentRepository.findById(event.getPaymentId());
        if (optionalIntent.isPresent()) {
            PaymentIntent intent = optionalIntent.get();
            intent.setStatus(PaymentStatus.CONFIRMED);
            paymentIntentRepository.update(intent);
            log.info("PaymentIntent {} atualizada para CONFIRMED", intent.getId());
        } else {
            log.warn("PaymentIntent {} não encontrada ao processar confirmação", event.getPaymentId());
        }
    }
}
