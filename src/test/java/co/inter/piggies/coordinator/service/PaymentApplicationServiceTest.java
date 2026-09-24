package co.inter.piggies.coordinator.service;

import co.inter.piggies.coordinator.api.model.CreatePaymentRequest;
import co.inter.piggies.coordinator.api.model.PaymentStatus;
import co.inter.piggies.coordinator.domain.DuplicatePayment;
import co.inter.piggies.coordinator.domain.FailureReasons;
import co.inter.piggies.coordinator.domain.InvalidPaymentException;
import co.inter.piggies.coordinator.domain.PaymentIntentEntity;
import co.inter.piggies.coordinator.domain.PaymentIntentStore;
import co.inter.piggies.coordinator.orchestration.PaymentOrchestration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.ThrowableProblem;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentApplicationServiceTest {

    private final UUID paymentId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private MemoryPayments payments;
    private RecordingOrchestration orchestration;
    private PaymentApplicationService service;

    @BeforeEach
    void setUp() {
        payments = new MemoryPayments();
        orchestration = new RecordingOrchestration();
        service = new PaymentApplicationService(payments, orchestration);
    }

    @Test
    void acceptsANewIntentAndStartsOrchestrationAfterInsert() {
        var accepted = service.accept(paymentId, request(100L));

        assertThat(accepted.getPaymentId()).isEqualTo(paymentId);
        assertThat(accepted.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(orchestration.started).containsExactly(paymentId);
        assertThat(payments.findById(paymentId)).isPresent();
    }

    @Test
    void returnsTheExistingIntentWithoutStartingAgain() {
        var existing = PaymentIntentEntity.start(paymentId, "12345678901", "0001", "000123", "12345678000199", 100L, Instant.parse("2026-09-24T19:46:00Z"));
        existing.fail(FailureReasons.MERCHANT_INACTIVE);
        payments.insert(existing);

        var accepted = service.accept(paymentId, request(200L));

        assertThat(accepted.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payments.findById(paymentId).orElseThrow().getAmount()).isEqualTo(100L);
        assertThat(orchestration.started).isEmpty();
    }

    @Test
    void returnsTheWinnerWhenInsertRacesOnTheSameId() {
        var winner = PaymentIntentEntity.start(paymentId, "12345678901", "0001", "000123", "12345678000199", 100L, Instant.parse("2026-09-24T19:46:00Z"));
        winner.confirm();
        payments.rows.put(paymentId, winner);
        payments.hideFirstFind = true;
        payments.duplicate = true;

        var accepted = service.accept(paymentId, request(100L));

        assertThat(accepted.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(orchestration.started).isEmpty();
    }

    @Test
    void rethrowsWhenTheDuplicateRowDisappears() {
        payments.hideFirstFind = true;
        payments.duplicate = true;

        assertThatThrownBy(() -> service.accept(paymentId, request(100L)))
                .isInstanceOf(RuntimeException.class)
                .matches(DuplicatePayment::matches);
        assertThat(orchestration.started).isEmpty();
    }

    @Test
    void rethrowsUnexpectedInsertErrors() {
        payments.insertError = new IllegalStateException("db down");

        assertThatThrownBy(() -> service.accept(paymentId, request(100L)))
                .isSameAs(payments.insertError);
        assertThat(orchestration.started).isEmpty();
    }

    @Test
    void rejectsANonPositiveAmountBeforeInsert() {
        assertThatThrownBy(() -> service.accept(paymentId, request(0L)))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessage(FailureReasons.AMOUNT_INVALID);
        assertThatThrownBy(() -> service.accept(null, request(100L)))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessage(FailureReasons.PAYMENT_ID_REQUIRED);
        assertThat(payments.rows).isEmpty();
        assertThat(orchestration.started).isEmpty();
    }

    @Test
    void returnsTheStoredIntent() {
        var intent = PaymentIntentEntity.start(paymentId, "12345678901", "0001", "000123", "12345678000199", 100L, Instant.parse("2026-09-24T19:46:00Z"));
        var reservationId = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        intent.attachReservation(reservationId);
        intent.fail(FailureReasons.MERCHANT_INACTIVE);
        payments.insert(intent);

        var view = service.get(paymentId);

        assertThat(view.getId()).isEqualTo(paymentId);
        assertThat(view.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(view.getReservationId()).isEqualTo(reservationId);
        assertThat(view.getFailureReason()).isEqualTo(FailureReasons.MERCHANT_INACTIVE);
        assertThat(view.getAmount()).isEqualTo(100L);
        assertThat(view.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsAnUnknownPayment() {
        assertThatThrownBy(() -> service.get(paymentId))
                .isInstanceOf(ThrowableProblem.class)
                .hasMessageContaining("Pagamento não encontrado");
    }

    private static CreatePaymentRequest request(Long amount) {
        return new CreatePaymentRequest("12345678901", "0001", "000123", "12345678000199", amount);
    }

    private static final class RecordingOrchestration implements PaymentOrchestration {
        private final List<UUID> started = new ArrayList<>();

        @Override
        public void start(UUID paymentId) {
            started.add(paymentId);
        }
    }

    private static final class MemoryPayments implements PaymentIntentStore {
        private final Map<UUID, PaymentIntentEntity> rows = new HashMap<>();
        private boolean hideFirstFind;
        private boolean duplicate;
        private RuntimeException insertError;
        private int finds;

        @Override
        public Optional<PaymentIntentEntity> findById(UUID id) {
            finds++;
            if (hideFirstFind && finds == 1) {
                return Optional.empty();
            }
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public void insert(PaymentIntentEntity intent) {
            if (duplicate) {
                throw new RuntimeException(new SQLException("duplicate key", "23505"));
            }
            if (insertError != null) {
                throw insertError;
            }
            rows.put(intent.getId(), intent);
        }

        @Override
        public void update(PaymentIntentEntity intent) {
            rows.put(intent.getId(), intent);
        }
    }
}
