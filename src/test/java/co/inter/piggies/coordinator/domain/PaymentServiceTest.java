package co.inter.piggies.coordinator.domain;

import co.inter.piggies.coordinator.support.InMemoryPaymentIntentStore;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PaymentServiceTest {

    private final PaymentService payments = new PaymentService(new InMemoryPaymentIntentStore());

    @Test
    void creditConfirmationClosesADebitedPayment() {
        UUID id = accept();
        payments.markDebited(id);

        payments.confirmCredited(id);

        assertThat(stage(id)).isEqualTo(Stage.CONFIRMED);
    }

    @Test
    void repeatedConfirmationIsIgnored() {
        UUID id = accept();
        payments.markDebited(id);
        payments.confirmCredited(id);

        assertThatCode(() -> payments.confirmCredited(id)).doesNotThrowAnyException();
        assertThat(stage(id)).isEqualTo(Stage.CONFIRMED);
    }

    @Test
    void confirmationForAFailedPaymentIsIgnored() {
        UUID id = accept();
        payments.fail(id, FailureReason.INSUFFICIENT_BALANCE);

        assertThatCode(() -> payments.confirmCredited(id)).doesNotThrowAnyException();
        assertThat(stage(id)).isEqualTo(Stage.FAILED);
    }

    @Test
    void confirmationForAnUnknownPaymentIsIgnored() {
        assertThatCode(() -> payments.confirmCredited(UUID.randomUUID())).doesNotThrowAnyException();
    }

    private UUID accept() {
        UUID id = UUID.randomUUID();
        payments.accept(new NewPayment(id, "12345678901", "0001", "000123", "12345678000199", 100));
        return id;
    }

    private Stage stage(UUID id) {
        return payments.find(id).orElseThrow().getStage();
    }
}
