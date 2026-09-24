package co.inter.piggies.coordinator.api;

import co.inter.piggies.coordinator.domain.FailureReasons;
import co.inter.piggies.coordinator.domain.InvalidPaymentException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidPaymentExceptionHandlerTest {

    @Test
    void rendersABadRequestProblem() {
        var response = new InvalidPaymentExceptionHandler().handle(null, new InvalidPaymentException(FailureReasons.AMOUNT_INVALID));

        assertThat(response.getStatus().getCode()).isEqualTo(400);
        assertThat(response.getBody().orElseThrow().getTitle()).isEqualTo("Requisição inválida");
        assertThat(response.getBody().orElseThrow().getDetail()).isEqualTo(FailureReasons.AMOUNT_INVALID);
    }
}
