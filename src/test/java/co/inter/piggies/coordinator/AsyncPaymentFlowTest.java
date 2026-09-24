package co.inter.piggies.coordinator;

import co.inter.piggies.coordinator.api.model.PaymentConfirmEvent;
import co.inter.piggies.coordinator.api.model.PaymentConfirmedEvent;
import co.inter.piggies.coordinator.api.model.PaymentStatus;
import co.inter.piggies.coordinator.domain.PaymentIntentEntity;
import co.inter.piggies.coordinator.domain.PaymentIntentStore;
import co.inter.piggies.coordinator.messaging.KafkaPaymentConfirmPublisher;
import co.inter.piggies.coordinator.messaging.PaymentConfirmKafkaClient;
import co.inter.piggies.coordinator.messaging.PaymentConfirmedListener;
import co.inter.piggies.coordinator.orchestration.AsyncPaymentOrchestration;
import co.inter.piggies.coordinator.orchestration.MerchantGateway;
import co.inter.piggies.coordinator.orchestration.MerchantOutcome;
import co.inter.piggies.coordinator.orchestration.PaymentConfirmPublisher;
import co.inter.piggies.coordinator.orchestration.PaymentOrchestrator;
import co.inter.piggies.coordinator.orchestration.ReserveGateway;
import co.inter.piggies.coordinator.orchestration.ReserveOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncPaymentFlowTest {

    private final UUID paymentId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    @Test
    @Timeout(5)
    void acceptanceReturnsBeforeReserveFinishes() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var payments = new SinglePayment();
        payments.intent = PaymentIntentEntity.start(paymentId, "12345678901", "0001", "000123", "12345678000199", 100L, Instant.parse("2026-09-24T19:46:00Z"));
        ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        var orchestrator = new PaymentOrchestrator(payments, new ReserveGateway() {
            @Override
            public ReserveOutcome reserve(PaymentIntentEntity intent) {
                entered.countDown();
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timeout");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return ReserveOutcome.reserved(UUID.randomUUID());
            }

            @Override
            public void confirm(UUID reservationId) {
            }

            @Override
            public void release(UUID reservationId) {
            }
        }, merchantCnpj -> MerchantOutcome.accepted(), event -> {
        }, pool);
        var async = new AsyncPaymentOrchestration(orchestrator);

        async.start(paymentId);

        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        async.shutdown();
        pool.shutdownNow();
    }

    @Test
    void listenerConfirmsTheIntentFromTheCreditEvent() {
        var payments = new SinglePayment();
        var intent = PaymentIntentEntity.start(paymentId, "12345678901", "0001", "000123", "12345678000199", 100L, Instant.parse("2026-09-24T19:46:00Z"));
        intent.attachReservation(UUID.randomUUID());
        payments.intent = intent;
        ExecutorService pool = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        var orchestrator = new PaymentOrchestrator(
                payments,
                new ReserveGateway() {
                    @Override
                    public ReserveOutcome reserve(PaymentIntentEntity ignored) {
                        return ReserveOutcome.rejected("não chamado");
                    }

                    @Override
                    public void confirm(UUID reservationId) {
                    }

                    @Override
                    public void release(UUID reservationId) {
                    }
                },
                merchantCnpj -> MerchantOutcome.accepted(),
                event -> {
                },
                pool
        );
        var listener = new PaymentConfirmedListener(orchestrator);

        listener.onConfirmed(new PaymentConfirmedEvent(paymentId, "12345678000199", 100L, ZonedDateTime.now(ZoneOffset.UTC)));

        assertThat(payments.intent.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        pool.shutdownNow();
    }

    @Test
    void publisherSendsTheConfirmEvent() {
        var sent = new AtomicReference<PaymentConfirmEvent>();
        PaymentConfirmKafkaClient client = sent::set;
        var publisher = new KafkaPaymentConfirmPublisher(client);
        var event = new PaymentConfirmEvent(paymentId, UUID.randomUUID(), "12345678000199", 100L);

        publisher.publish(event);

        assertThat(sent.get()).isSameAs(event);
    }

    private static final class SinglePayment implements PaymentIntentStore {
        private PaymentIntentEntity intent;

        @Override
        public Optional<PaymentIntentEntity> findById(UUID id) {
            return Optional.ofNullable(intent).filter(stored -> stored.getId().equals(id));
        }

        @Override
        public void insert(PaymentIntentEntity intent) {
            this.intent = intent;
        }

        @Override
        public void update(PaymentIntentEntity intent) {
            this.intent = intent;
        }
    }
}
