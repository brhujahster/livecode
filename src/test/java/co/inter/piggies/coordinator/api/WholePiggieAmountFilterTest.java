package co.inter.piggies.coordinator.api;

import co.inter.piggies.coordinator.domain.FailureReasons;
import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class WholePiggieAmountFilterTest {

    private final WholePiggieAmountFilter filter = new WholePiggieAmountFilter();

    @Test
    void letsIntegerAmountsThrough() {
        assertThat(filter.rejectFractionalAmount(HttpRequest.GET("/v1/payments"), null)).isNull();
        assertThat(filter.rejectFractionalAmount(HttpRequest.POST("/v1/payments", new byte[0]), new byte[0])).isNull();
        assertThat(filter.rejectFractionalAmount(HttpRequest.POST("/v1/payments", "{\"amount\":100}"), "{\"amount\":100}".getBytes(UTF_8))).isNull();
    }

    @Test
    void rejectsAFractionalAmount() {
        var response = filter.rejectFractionalAmount(
                HttpRequest.POST("/v1/payments", "{\"amount\":10.5}"),
                "{\"amount\":10.5}".getBytes(UTF_8)
        );

        assertThat(response.getStatus().getCode()).isEqualTo(400);
        assertThat(response.body().toString()).contains(FailureReasons.AMOUNT_INVALID);
    }
}
