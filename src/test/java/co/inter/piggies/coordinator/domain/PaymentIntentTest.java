package co.inter.piggies.coordinator.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentIntentTest {

    @Test
    void startsAcceptedAndProcessing() {
        PaymentIntent intent = newIntent();

        assertThat(intent.getStage()).isEqualTo(Stage.ACCEPTED);
        assertThat(intent.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(intent.getFailureReason()).isNull();
    }

    @Test
    void debitedIsStillProcessing() {
        PaymentIntent intent = newIntent();

        intent.markDebited();

        assertThat(intent.getStage()).isEqualTo(Stage.DEBITED);
        assertThat(intent.status()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void confirmsAfterDebit() {
        PaymentIntent intent = newIntent();
        intent.markDebited();

        intent.confirm();

        assertThat(intent.status()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    void failsBeforeDebitWithReason() {
        PaymentIntent intent = newIntent();

        intent.fail(FailureReason.MERCHANT_INACTIVE);

        assertThat(intent.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(intent.getFailureReason()).isEqualTo(FailureReason.MERCHANT_INACTIVE);
    }

    @Test
    void cannotConfirmWithoutDebit() {
        assertThatThrownBy(() -> newIntent().confirm()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotFailAfterDebit() {
        PaymentIntent intent = newIntent();
        intent.markDebited();

        assertThatThrownBy(() -> intent.fail(FailureReason.MERCHANT_INACTIVE)).isInstanceOf(IllegalStateException.class);
        assertThat(intent.getStage()).isEqualTo(Stage.DEBITED);
    }

    @Test
    void finalStagesDoNotChange() {
        PaymentIntent confirmed = newIntent();
        confirmed.markDebited();
        confirmed.confirm();
        PaymentIntent failed = newIntent();
        failed.fail(FailureReason.INSUFFICIENT_BALANCE);

        assertThatThrownBy(confirmed::markDebited).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> confirmed.fail(FailureReason.INSUFFICIENT_BALANCE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(failed::markDebited).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.fail(FailureReason.MERCHANT_NOT_FOUND)).isInstanceOf(IllegalStateException.class);
        assertThat(failed.getFailureReason()).isEqualTo(FailureReason.INSUFFICIENT_BALANCE);
    }

    private static PaymentIntent newIntent() {
        return new PaymentIntent(UUID.randomUUID(), "12345678901", "0001", "000123", "12345678000199", 100);
    }
}
