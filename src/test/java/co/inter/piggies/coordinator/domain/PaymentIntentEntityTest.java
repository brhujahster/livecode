package co.inter.piggies.coordinator.domain;

import co.inter.piggies.coordinator.api.model.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentIntentEntityTest {

    private final UUID id = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private final Instant createdAt = Instant.parse("2026-09-24T19:46:00Z");

    @Test
    void startsProcessing() {
        var intent = payment(100L);

        assertThat(intent.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(intent.getAmount()).isEqualTo(100L);
        assertThat(intent.getPayerCpf()).isEqualTo("12345678901");
        assertThat(intent.getMerchantCnpj()).isEqualTo("12345678000199");
        assertThat(intent.getReservationId()).isNull();
        assertThat(intent.getFailureReason()).isNull();
        assertThat(intent.getCreatedAt()).isEqualTo(createdAt);
        assertThat(intent.getUpdatedAt()).isEqualTo(createdAt);
    }

    @Test
    void rejectsMissingIdAndNonPositiveAmount() {
        assertThatThrownBy(() -> PaymentIntentEntity.start(null, "12345678901", "0001", "000123", "12345678000199", 100L, createdAt))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessage(FailureReasons.PAYMENT_ID_REQUIRED);
        assertThatThrownBy(() -> PaymentIntentEntity.start(id, "12345678901", "0001", "000123", "12345678000199", null, createdAt))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessage(FailureReasons.AMOUNT_INVALID);
        assertThatThrownBy(() -> PaymentIntentEntity.start(id, "12345678901", "0001", "000123", "12345678000199", 0L, createdAt))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessage(FailureReasons.AMOUNT_INVALID);
        assertThatThrownBy(() -> PaymentIntentEntity.start(id, "12345678901", "0001", "000123", "12345678000199", -5L, createdAt))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessage(FailureReasons.AMOUNT_INVALID);
    }

    @Test
    void attachesReservationConfirmsAndFailsOnlyWhileProcessing() {
        var intent = payment(100L);
        var reservationId = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

        intent.attachReservation(reservationId);
        assertThat(intent.getReservationId()).isEqualTo(reservationId);
        assertThat(intent.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        intent.confirm();
        assertThat(intent.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(intent.getFailureReason()).isNull();
        assertThat(intent.getUpdatedAt()).isAfterOrEqualTo(createdAt);

        assertThatThrownBy(intent::confirm).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> intent.fail("depois")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> intent.attachReservation(reservationId)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void normalizesFailureReason() {
        var blank = payment(100L);
        blank.fail("   ");
        assertThat(blank.getFailureReason()).isEqualTo(FailureReasons.ORCHESTRATION_FAILED);
        assertThat(blank.getStatus()).isEqualTo(PaymentStatus.FAILED);

        var trimmed = payment(100L);
        trimmed.fail("  Merchant inativo  ");
        assertThat(trimmed.getFailureReason()).isEqualTo("Merchant inativo");

        var missing = payment(100L);
        missing.fail(null);
        assertThat(missing.getFailureReason()).isEqualTo(FailureReasons.ORCHESTRATION_FAILED);

        var huge = payment(100L);
        huge.fail("x".repeat(501));
        assertThat(huge.getFailureReason()).hasSize(500);
    }

    private PaymentIntentEntity payment(Long amount) {
        return PaymentIntentEntity.start(id, "12345678901", "0001", "000123", "12345678000199", amount, createdAt);
    }
}
