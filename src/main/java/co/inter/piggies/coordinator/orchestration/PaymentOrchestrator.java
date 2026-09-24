package co.inter.piggies.coordinator.orchestration;

import co.inter.piggies.coordinator.api.model.PaymentConfirmEvent;
import co.inter.piggies.coordinator.api.model.PaymentConfirmedEvent;
import co.inter.piggies.coordinator.api.model.PaymentStatus;
import co.inter.piggies.coordinator.domain.FailureReasons;
import co.inter.piggies.coordinator.domain.PaymentIntentEntity;
import co.inter.piggies.coordinator.domain.PaymentIntentStore;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

@Singleton
public class PaymentOrchestrator {

    private final PaymentIntentStore payments;
    private final ReserveGateway reserve;
    private final MerchantGateway merchant;
    private final PaymentConfirmPublisher publisher;
    private final ExecutorService parallel;
    private final boolean shutdownParallel;

    @Inject
    public PaymentOrchestrator(
            PaymentIntentStore payments,
            ReserveGateway reserve,
            MerchantGateway merchant,
            PaymentConfirmPublisher publisher
    ) {
        this(payments, reserve, merchant, publisher, newPool(), true);
    }

    public PaymentOrchestrator(
            PaymentIntentStore payments,
            ReserveGateway reserve,
            MerchantGateway merchant,
            PaymentConfirmPublisher publisher,
            ExecutorService parallel
    ) {
        this(payments, reserve, merchant, publisher, parallel, false);
    }

    private PaymentOrchestrator(
            PaymentIntentStore payments,
            ReserveGateway reserve,
            MerchantGateway merchant,
            PaymentConfirmPublisher publisher,
            ExecutorService parallel,
            boolean shutdownParallel
    ) {
        this.payments = payments;
        this.reserve = reserve;
        this.merchant = merchant;
        this.publisher = publisher;
        this.parallel = parallel;
        this.shutdownParallel = shutdownParallel;
    }

    @Transactional
    public void orchestrate(UUID paymentId) {
        PaymentIntentEntity intent = payments.findById(paymentId).orElse(null);
        if (intent == null || intent.getStatus() != PaymentStatus.PROCESSING) {
            return;
        }
        CompletableFuture<ReserveOutcome> reserveFuture = CompletableFuture.supplyAsync(() -> reserve.reserve(intent), parallel);
        CompletableFuture<MerchantOutcome> merchantFuture = CompletableFuture.supplyAsync(
                () -> merchant.validate(intent.getMerchantCnpj()),
                parallel
        );
        ReserveOutcome reserved = await(reserveFuture, ReserveOutcome::rejected);
        MerchantOutcome validated = await(merchantFuture, MerchantOutcome::rejected);

        boolean reserveFailed = !reserved.succeeded() || reserved.reservationId() == null;
        if (reserveFailed || !validated.succeeded()) {
            fail(intent, reserved.reservationId(), reason(reserved, validated));
            return;
        }

        intent.attachReservation(reserved.reservationId());
        payments.update(intent);

        try {
            reserve.confirm(reserved.reservationId());
        } catch (RuntimeException exception) {
            fail(intent, reserved.reservationId(), rootMessage(exception));
            return;
        }

        try {
            publisher.publish(new PaymentConfirmEvent(
                    intent.getId(),
                    reserved.reservationId(),
                    intent.getMerchantCnpj(),
                    intent.getAmount()
            ));
        } catch (RuntimeException exception) {
            intent.fail(FailureReasons.PUBLISH_FAILED);
            payments.update(intent);
        }
    }

    @Transactional
    public void onCreditConfirmed(PaymentConfirmedEvent event) {
        if (event == null || event.getPaymentId() == null) {
            return;
        }
        PaymentIntentEntity intent = payments.findById(event.getPaymentId()).orElse(null);
        if (intent == null || intent.getStatus() != PaymentStatus.PROCESSING || intent.getReservationId() == null) {
            return;
        }
        if (event.getMerchantCnpj() == null
                || !event.getMerchantCnpj().equals(intent.getMerchantCnpj())
                || event.getAmount() == null
                || event.getAmount() != intent.getAmount()) {
            intent.fail(FailureReasons.DIVERGENT_CREDIT);
            payments.update(intent);
            return;
        }
        intent.confirm();
        payments.update(intent);
    }

    @PreDestroy
    void shutdown() {
        if (shutdownParallel) {
            parallel.shutdown();
        }
    }

    private void fail(PaymentIntentEntity intent, UUID reservationId, String reason) {
        if (reservationId != null) {
            try {
                reserve.release(reservationId);
            } catch (RuntimeException exception) {
                reason = reason + FailureReasons.RELEASE_FAILED_SUFFIX;
            }
        }
        intent.fail(reason);
        payments.update(intent);
    }

    private static String reason(ReserveOutcome reserved, MerchantOutcome validated) {
        boolean reserveFailed = !reserved.succeeded() || reserved.reservationId() == null;
        boolean merchantFailed = !validated.succeeded();
        if (reserveFailed && merchantFailed) {
            return text(reserved.failureReason()) + "; " + text(validated.failureReason());
        }
        if (reserveFailed) {
            return text(reserved.failureReason());
        }
        return text(validated.failureReason());
    }

    private static String text(String reason) {
        if (reason == null || reason.isBlank()) {
            return FailureReasons.ORCHESTRATION_FAILED;
        }
        return reason.trim();
    }

    private static <T> T await(CompletableFuture<T> future, Function<String, T> rejected) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            return rejected.apply(rootMessage(exception));
        }
    }

    static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return FailureReasons.ORCHESTRATION_FAILED;
        }
        return message;
    }

    private static ExecutorService newPool() {
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "payment-parallel");
            thread.setDaemon(true);
            return thread;
        });
    }
}
