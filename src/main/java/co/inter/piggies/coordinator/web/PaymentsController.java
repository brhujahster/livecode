package co.inter.piggies.coordinator.web;

import co.inter.piggies.coordinator.domain.NewPayment;
import co.inter.piggies.coordinator.domain.PaymentService;
import co.inter.piggies.coordinator.orchestration.PaymentOrchestrator;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.UUID;

@Controller("/v1/payments")
class PaymentsController {

    private final PaymentService payments;
    private final PaymentOrchestrator orchestrator;

    PaymentsController(PaymentService payments, PaymentOrchestrator orchestrator) {
        this.payments = payments;
        this.orchestrator = orchestrator;
    }

    @Post
    HttpResponse<PaymentAccepted> create(@Header("Idempotency-Key") UUID paymentId, @Body @Valid CreatePaymentRequest request) {
        PaymentService.Accepted accepted = payments.accept(new NewPayment(
                paymentId,
                request.payer().cpf(),
                request.payer().agency(),
                request.payer().accountNumber(),
                request.qrCode().merchantCnpj(),
                request.qrCode().amount()));
        if (accepted.created()) {
            orchestrator.start(paymentId);
        }
        return HttpResponse.<PaymentAccepted>accepted(URI.create("/v1/payments/" + paymentId))
                .body(new PaymentAccepted(paymentId, accepted.intent().status()));
    }

    @Get("/{paymentId}")
    Payment get(UUID paymentId) {
        return payments.find(paymentId)
                .map(Payment::from)
                .orElseThrow(() -> new ApiProblemException(ApiProblem.of(
                        404, "Pagamento não encontrado", "Nenhum pagamento com id " + paymentId, "PAYMENT_NOT_FOUND")));
    }
}
