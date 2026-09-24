package co.inter.piggies.controller;

import co.inter.piggies.coordinator.api.PaymentsApi;
import co.inter.piggies.coordinator.api.model.CreatePaymentRequest;
import co.inter.piggies.coordinator.api.model.PaymentAccepted;
import co.inter.piggies.coordinator.api.model.PaymentIntent;
import co.inter.piggies.coordinator.service.PaymentApplicationService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;

import java.util.UUID;

@Controller
@Singleton
@ExecuteOn(TaskExecutors.BLOCKING)
public class PiggiesPaymentCoordinator implements PaymentsApi {

    private final PaymentApplicationService payments;

    public PiggiesPaymentCoordinator(PaymentApplicationService payments) {
        this.payments = payments;
    }

    @Override
    public HttpResponse<PaymentAccepted> createPayment(UUID idempotencyKey, CreatePaymentRequest createPaymentRequest) {
        return HttpResponse.accepted().body(payments.accept(idempotencyKey, createPaymentRequest));
    }

    @Override
    public PaymentIntent getPayment(UUID id) {
        return payments.get(id);
    }
}
