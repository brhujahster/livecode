package co.inter.piggies.coordinator.orchestration;

import co.inter.piggies.coordinator.domain.FailureReason;
import co.inter.piggies.coordinator.domain.MerchantGateway;
import co.inter.piggies.coordinator.domain.PaymentEventPublisher;
import co.inter.piggies.coordinator.domain.PaymentIntent;
import co.inter.piggies.coordinator.domain.PaymentService;
import co.inter.piggies.coordinator.domain.ReserveGateway;
import co.inter.piggies.coordinator.domain.Stage;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Leva um pagamento aceito até FAILED ou até DEBITED com o evento de débito publicado. Reserva e validação do
 * merchant rodam em paralelo. O CONFIRMED vem depois, quando o merchant avisa que creditou.
 * Uma falha técnica em qualquer etapa deixa o pagamento no estágio em que estava, para ser retomado depois.
 */
@Singleton
public class PaymentOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentOrchestrator.class);

    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(10);

    private final PaymentService payments;
    private final ReserveGateway reserve;
    private final MerchantGateway merchant;
    private final PaymentEventPublisher events;
    private final ExecutorService executor;

    @Inject
    public PaymentOrchestrator(PaymentService payments, ReserveGateway reserve, MerchantGateway merchant,
                               PaymentEventPublisher events) {
        this(payments, reserve, merchant, events,
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("payment-", 0).factory()));
    }

    PaymentOrchestrator(PaymentService payments, ReserveGateway reserve, MerchantGateway merchant,
                        PaymentEventPublisher events, ExecutorService executor) {
        this.payments = payments;
        this.reserve = reserve;
        this.merchant = merchant;
        this.events = events;
        this.executor = executor;
    }

    public void start(UUID paymentId) {
        executor.execute(() -> process(paymentId));
    }

    /**
     * Espera os pagamentos em andamento antes de o banco ser fechado. Sem isso, uma etapa no meio de uma
     * transação pode encontrar o contexto já desligado. O que não terminar no prazo fica no estágio atual.
     * Usa o ShutdownEvent e não @PreDestroy porque o SessionFactory pode ser destruído antes deste bean.
     */
    @EventListener
    void onShutdown(ShutdownEvent event) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
            LOG.warn("Pagamentos ainda em andamento após {}; serão retomados depois", SHUTDOWN_GRACE);
            executor.shutdownNow();
        }
    }

    public void process(UUID paymentId) {
        try {
            PaymentIntent intent = payments.find(paymentId).orElseThrow();
            if (intent.getStage() != Stage.ACCEPTED) {
                return;
            }
            settle(intent);
        } catch (RuntimeException e) {
            LOG.error("Falha técnica ao processar o pagamento {}; ele fica no estágio atual", paymentId, e);
        }
    }

    private void settle(PaymentIntent intent) {
        UUID paymentId = intent.getId();
        var reservation = CompletableFuture.supplyAsync(() -> reserve.reserve(intent), executor);
        var validation = CompletableFuture.supplyAsync(() -> merchant.validate(intent.getMerchantCnpj()), executor);

        Optional<FailureReason> reserveFailure = reservation.join();
        Optional<FailureReason> merchantFailure = validation.join();

        if (reserveFailure.isPresent()) {
            payments.fail(paymentId, reserveFailure.get());
            return;
        }
        if (merchantFailure.isPresent()) {
            reserve.release(paymentId);
            payments.fail(paymentId, merchantFailure.get());
            return;
        }

        reserve.confirm(paymentId);
        PaymentIntent debited = payments.markDebited(paymentId);
        events.debited(debited);
    }
}
