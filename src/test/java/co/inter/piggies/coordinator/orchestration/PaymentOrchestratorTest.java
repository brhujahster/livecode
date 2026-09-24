package co.inter.piggies.coordinator.orchestration;

import co.inter.piggies.coordinator.api.model.PaymentConfirmEvent;
import co.inter.piggies.coordinator.api.model.PaymentConfirmedEvent;
import co.inter.piggies.coordinator.api.model.PaymentStatus;
import co.inter.piggies.coordinator.domain.FailureReasons;
import co.inter.piggies.coordinator.domain.PaymentIntentEntity;
import co.inter.piggies.coordinator.domain.PaymentIntentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentOrchestratorTest {

    private final UUID paymentId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private final UUID reservationId = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    private ExecutorService pool;
    private MemoryPayments payments;
    private FakeReserve reserve;
    private FakeMerchant merchant;
    private FakePublisher publisher;
    private PaymentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        payments = new MemoryPayments();
        reserve = new FakeReserve();
        merchant = new FakeMerchant();
        publisher = new FakePublisher();
        reserve.outcome = ReserveOutcome.reserved(reservationId);
        merchant.outcome = MerchantOutcome.accepted();
        orchestrator = new PaymentOrchestrator(payments, reserve, merchant, publisher, pool);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    @Timeout(5)
    void reservesAndValidatesInParallelThenPublishesWithoutConfirmingTheIntent() throws Exception {
        var reserveEntered = new CountDownLatch(1);
        var merchantEntered = new CountDownLatch(1);
        reserve.before = () -> {
            reserveEntered.countDown();
            await(merchantEntered);
        };
        merchant.before = () -> {
            merchantEntered.countDown();
            await(reserveEntered);
        };
        payments.save(processing());

        orchestrator.orchestrate(paymentId);

        PaymentIntentEntity stored = payments.findById(paymentId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(stored.getReservationId()).isEqualTo(reservationId);
        assertThat(reserve.confirms).containsExactly(reservationId);
        assertThat(reserve.releases).isEmpty();
        assertThat(publisher.events).containsExactly(new PaymentConfirmEvent(paymentId, reservationId, "12345678000199", 100L));
    }

    @Test
    void marksFailedWhenMerchantIsInactiveAndReleasesTheReservation() {
        merchant.outcome = MerchantOutcome.rejected(FailureReasons.MERCHANT_INACTIVE);
        payments.save(processing());

        orchestrator.orchestrate(paymentId);

        PaymentIntentEntity stored = payments.findById(paymentId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(stored.getFailureReason()).isEqualTo(FailureReasons.MERCHANT_INACTIVE);
        assertThat(reserve.releases).containsExactly(reservationId);
        assertThat(reserve.confirms).isEmpty();
        assertThat(publisher.events).isEmpty();
    }

    @Test
    void marksFailedWhenBalanceIsInsufficientWithoutReleasing() {
        reserve.outcome = ReserveOutcome.rejected(FailureReasons.INSUFFICIENT_FUNDS);
        payments.save(processing());

        orchestrator.orchestrate(paymentId);

        PaymentIntentEntity stored = payments.findById(paymentId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(stored.getFailureReason()).isEqualTo(FailureReasons.INSUFFICIENT_FUNDS);
        assertThat(reserve.releases).isEmpty();
        assertThat(publisher.events).isEmpty();
    }

    @Test
    void keepsBothReasonsWhenReserveAndMerchantFail() {
        reserve.outcome = ReserveOutcome.rejected(FailureReasons.ACCOUNT_NOT_FOUND);
        merchant.outcome = MerchantOutcome.rejected(FailureReasons.MERCHANT_NOT_FOUND);
        payments.save(processing());

        orchestrator.orchestrate(paymentId);

        assertThat(payments.findById(paymentId).orElseThrow().getFailureReason())
                .isEqualTo(FailureReasons.ACCOUNT_NOT_FOUND + "; " + FailureReasons.MERCHANT_NOT_FOUND);
        assertThat(reserve.releases).isEmpty();
    }

    @Test
    void normalizesBlankDownstreamReasons() {
        reserve.outcome = ReserveOutcome.rejected("  ");
        merchant.outcome = MerchantOutcome.rejected(null);
        payments.save(processing());

        orchestrator.orchestrate(paymentId);

        assertThat(payments.findById(paymentId).orElseThrow().getFailureReason())
                .isEqualTo(FailureReasons.ORCHESTRATION_FAILED + "; " + FailureReasons.ORCHESTRATION_FAILED);
    }

    @Test
    void treatsSuccessfulReserveWithoutIdAsFailure() {
        reserve.outcome = new ReserveOutcome(null, null, true);
        payments.save(processing());

        orchestrator.orchestrate(paymentId);

        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payments.findById(paymentId).orElseThrow().getFailureReason()).isEqualTo(FailureReasons.ORCHESTRATION_FAILED);
        assertThat(reserve.releases).isEmpty();
    }

    @Test
    void releasesWhenDebitConfirmationFails() {
        reserve.confirmError = new IllegalStateException("Reserva liberada não pode ser confirmada");
        payments.save(processing());

        orchestrator.orchestrate(paymentId);

        PaymentIntentEntity stored = payments.findById(paymentId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(stored.getFailureReason()).isEqualTo("Reserva liberada não pode ser confirmada");
        assertThat(stored.getReservationId()).isEqualTo(reservationId);
        assertThat(reserve.releases).containsExactly(reservationId);
        assertThat(publisher.events).isEmpty();
    }

    @Test
    void stillFailsWhenReleaseAlsoFails() {
        merchant.outcome = MerchantOutcome.rejected(FailureReasons.MERCHANT_INACTIVE);
        reserve.releaseError = new IllegalStateException("release down");
        payments.save(processing());

        orchestrator.orchestrate(paymentId);

        assertThat(payments.findById(paymentId).orElseThrow().getFailureReason())
                .isEqualTo(FailureReasons.MERCHANT_INACTIVE + FailureReasons.RELEASE_FAILED_SUFFIX);
    }

    @Test
    void doesNotReleaseWhenPublishingFailsAfterTheDebit() {
        publisher.error = new IllegalStateException("kafka down");
        payments.save(processing());

        orchestrator.orchestrate(paymentId);

        PaymentIntentEntity stored = payments.findById(paymentId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(stored.getFailureReason()).isEqualTo(FailureReasons.PUBLISH_FAILED);
        assertThat(reserve.confirms).containsExactly(reservationId);
        assertThat(reserve.releases).isEmpty();
    }

    @Test
    void convertsUnexpectedDownstreamErrorsIntoFailure() {
        reserve.error = new RuntimeException("timeout");
        merchant.error = new IllegalStateException("  ");
        payments.save(processing());

        orchestrator.orchestrate(paymentId);

        assertThat(payments.findById(paymentId).orElseThrow().getFailureReason())
                .isEqualTo("timeout; " + FailureReasons.ORCHESTRATION_FAILED);
        assertThat(reserve.releases).isEmpty();
    }

    @Test
    void usesTheRootCauseWhenMerchantValidationCrashesAfterTheReserve() {
        merchant.error = new RuntimeException(new IllegalStateException("merchant down"));
        payments.save(processing());

        orchestrator.orchestrate(paymentId);

        assertThat(payments.findById(paymentId).orElseThrow().getFailureReason()).isEqualTo("merchant down");
        assertThat(reserve.releases).containsExactly(reservationId);
    }

    @Test
    void ignoresMissingAndTerminalIntents() {
        orchestrator.orchestrate(paymentId);
        assertThat(reserve.calls).isZero();

        payments.save(processing());
        payments.findById(paymentId).orElseThrow().fail("já falhou");
        orchestrator.orchestrate(paymentId);
        assertThat(reserve.calls).isZero();

        var confirmed = processing();
        confirmed.attachReservation(reservationId);
        confirmed.confirm();
        payments.save(confirmed);
        orchestrator.orchestrate(paymentId);
        assertThat(reserve.calls).isZero();
    }

    @Test
    void confirmsTheIntentWhenTheCreditEventMatches() {
        var intent = processing();
        intent.attachReservation(reservationId);
        payments.save(intent);

        orchestrator.onCreditConfirmed(event("12345678000199", 100L));

        PaymentIntentEntity stored = payments.findById(paymentId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(stored.getFailureReason()).isNull();
    }

    @Test
    void rejectsDivergentCreditEvents() {
        var intent = processing();
        intent.attachReservation(reservationId);
        payments.save(intent);

        orchestrator.onCreditConfirmed(event(null, 100L));
        assertThat(payments.findById(paymentId).orElseThrow().getFailureReason()).isEqualTo(FailureReasons.DIVERGENT_CREDIT);

        var otherCnpj = processing();
        otherCnpj.attachReservation(reservationId);
        payments.save(otherCnpj);
        orchestrator.onCreditConfirmed(event("00000000000000", 100L));
        assertThat(payments.findById(paymentId).orElseThrow().getFailureReason()).isEqualTo(FailureReasons.DIVERGENT_CREDIT);

        var missingAmount = processing();
        missingAmount.attachReservation(reservationId);
        payments.save(missingAmount);
        orchestrator.onCreditConfirmed(event("12345678000199", null));
        assertThat(payments.findById(paymentId).orElseThrow().getFailureReason()).isEqualTo(FailureReasons.DIVERGENT_CREDIT);

        var otherAmount = processing();
        otherAmount.attachReservation(reservationId);
        payments.save(otherAmount);
        orchestrator.onCreditConfirmed(event("12345678000199", 90L));
        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reserve.releases).isEmpty();
    }

    @Test
    void ignoresCreditEventsThatCannotCloseTheIntent() {
        orchestrator.onCreditConfirmed(null);
        orchestrator.onCreditConfirmed(new PaymentConfirmedEvent(null, "12345678000199", 100L, ZonedDateTime.now(ZoneOffset.UTC)));
        orchestrator.onCreditConfirmed(event("12345678000199", 100L));
        assertThat(payments.rows).isEmpty();

        var processing = processing();
        payments.save(processing);
        orchestrator.onCreditConfirmed(event("12345678000199", 100L));
        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        processing.attachReservation(reservationId);
        processing.confirm();
        orchestrator.onCreditConfirmed(event("12345678000199", 100L));
        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CONFIRMED);

        var failed = processing();
        failed.fail(FailureReasons.INSUFFICIENT_FUNDS);
        payments.save(failed);
        orchestrator.onCreditConfirmed(event("12345678000199", 100L));
        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    private PaymentIntentEntity processing() {
        return PaymentIntentEntity.start(paymentId, "12345678901", "0001", "000123", "12345678000199", 100L, Instant.parse("2026-09-24T19:46:00Z"));
    }

    private PaymentConfirmedEvent event(String cnpj, Long amount) {
        return new PaymentConfirmedEvent(paymentId, cnpj, amount, ZonedDateTime.parse("2026-09-24T19:46:02Z"));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("As chamadas não aconteceram em paralelo");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private final class FakeReserve implements ReserveGateway {
        private ReserveOutcome outcome;
        private RuntimeException error;
        private RuntimeException confirmError;
        private RuntimeException releaseError;
        private Runnable before = () -> {
        };
        private final List<UUID> confirms = new ArrayList<>();
        private final List<UUID> releases = new ArrayList<>();
        private int calls;

        @Override
        public ReserveOutcome reserve(PaymentIntentEntity intent) {
            calls++;
            before.run();
            if (error != null) {
                throw error;
            }
            return outcome;
        }

        @Override
        public void confirm(UUID id) {
            if (confirmError != null) {
                throw confirmError;
            }
            confirms.add(id);
        }

        @Override
        public void release(UUID id) {
            if (releaseError != null) {
                throw releaseError;
            }
            releases.add(id);
        }
    }

    private final class FakeMerchant implements MerchantGateway {
        private MerchantOutcome outcome;
        private RuntimeException error;
        private Runnable before = () -> {
        };

        @Override
        public MerchantOutcome validate(String merchantCnpj) {
            before.run();
            if (error != null) {
                throw error;
            }
            return outcome;
        }
    }

    private static final class FakePublisher implements PaymentConfirmPublisher {
        private final List<PaymentConfirmEvent> events = new ArrayList<>();
        private RuntimeException error;

        @Override
        public void publish(PaymentConfirmEvent event) {
            if (error != null) {
                throw error;
            }
            events.add(event);
        }
    }

    private static final class MemoryPayments implements PaymentIntentStore {
        private final java.util.Map<UUID, PaymentIntentEntity> rows = new java.util.HashMap<>();

        @Override
        public Optional<PaymentIntentEntity> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public void insert(PaymentIntentEntity intent) {
            rows.put(intent.getId(), intent);
        }

        @Override
        public void update(PaymentIntentEntity intent) {
            rows.put(intent.getId(), intent);
        }

        private void save(PaymentIntentEntity intent) {
            rows.put(intent.getId(), intent);
        }
    }
}
