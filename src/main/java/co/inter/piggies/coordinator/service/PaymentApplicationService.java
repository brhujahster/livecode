package co.inter.piggies.coordinator.service;

import co.inter.piggies.coordinator.api.model.CreatePaymentRequest;
import co.inter.piggies.coordinator.api.model.PaymentAccepted;
import co.inter.piggies.coordinator.api.model.PaymentIntent;
import co.inter.piggies.coordinator.domain.DuplicatePayment;
import co.inter.piggies.coordinator.domain.PaymentIntentEntity;
import co.inter.piggies.coordinator.domain.PaymentIntentStore;
import co.inter.piggies.coordinator.orchestration.PaymentOrchestration;
import jakarta.inject.Singleton;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

@Singleton
public class PaymentApplicationService {

    private final PaymentIntentStore payments;
    private final PaymentOrchestration orchestration;

    public PaymentApplicationService(PaymentIntentStore payments, PaymentOrchestration orchestration) {
        this.payments = payments;
        this.orchestration = orchestration;
    }

    public PaymentAccepted accept(UUID paymentId, CreatePaymentRequest request) {
        var existing = payments.findById(paymentId);
        if (existing.isPresent()) {
            return accepted(existing.get());
        }
        var intent = PaymentIntentEntity.start(
                paymentId,
                request.getPayerCpf(),
                request.getPayerAgency(),
                request.getPayerAccount(),
                request.getMerchantCnpj(),
                request.getAmount(),
                Instant.now()
        );
        try {
            payments.insert(intent);
        } catch (RuntimeException exception) {
            if (!DuplicatePayment.matches(exception)) {
                throw exception;
            }
            return payments.findById(paymentId).map(PaymentApplicationService::accepted).orElseThrow(() -> exception);
        }
        orchestration.start(paymentId);
        return accepted(intent);
    }

    public PaymentIntent get(UUID id) {
        return payments.findById(id).map(PaymentApplicationService::toApi).orElseThrow(PaymentApplicationService::notFound);
    }

    private static PaymentAccepted accepted(PaymentIntentEntity intent) {
        return new PaymentAccepted(intent.getId(), intent.getStatus());
    }

    static PaymentIntent toApi(PaymentIntentEntity intent) {
        var view = new PaymentIntent(
                intent.getId(),
                intent.getPayerCpf(),
                intent.getPayerAgency(),
                intent.getPayerAccount(),
                intent.getMerchantCnpj(),
                intent.getAmount(),
                intent.getStatus(),
                zoned(intent.getCreatedAt()),
                zoned(intent.getUpdatedAt())
        );
        view.setReservationId(intent.getReservationId());
        view.setFailureReason(intent.getFailureReason());
        return view;
    }

    private static ZonedDateTime zoned(Instant instant) {
        return instant.atZone(ZoneOffset.UTC);
    }

    private static RuntimeException notFound() {
        return Problem.builder()
                .withTitle("Pagamento não encontrado")
                .withStatus(Status.NOT_FOUND)
                .withDetail("Não existe intenção para o id informado")
                .build();
    }
}
