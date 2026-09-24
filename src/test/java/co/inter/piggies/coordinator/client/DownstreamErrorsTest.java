package co.inter.piggies.coordinator.client;

import co.inter.piggies.coordinator.domain.FailureReasons;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamErrorsTest {

    @ParameterizedTest
    @CsvSource({
            "400, true",
            "404, true",
            "409, true",
            "422, true",
            "500, false",
            "201, false"
    })
    void classifiesBusinessRejections(int code, boolean rejection) {
        assertThat(DownstreamErrors.isRejection(HttpStatus.valueOf(code))).isEqualTo(rejection);
    }

    @Test
    void readsDetailOrFallsBack() {
        assertThat(DownstreamErrors.detail(error(HttpStatus.UNPROCESSABLE_ENTITY, "{\"detail\":\"Saldo insuficiente\"}"), "fallback"))
                .isEqualTo("Saldo insuficiente");
        assertThat(DownstreamErrors.detail(error(HttpStatus.NOT_FOUND, "{\"title\":\"x\"}"), FailureReasons.ACCOUNT_NOT_FOUND))
                .isEqualTo(FailureReasons.ACCOUNT_NOT_FOUND);
        assertThat(DownstreamErrors.detail(error(HttpStatus.BAD_REQUEST, null), FailureReasons.ORCHESTRATION_FAILED))
                .isEqualTo(FailureReasons.ORCHESTRATION_FAILED);
    }

    @Test
    void parsesDetailField() {
        assertThat(DownstreamErrors.parseDetail(null)).isNull();
        assertThat(DownstreamErrors.parseDetail("  ")).isNull();
        assertThat(DownstreamErrors.parseDetail("{\"title\":\"x\"}")).isNull();
        assertThat(DownstreamErrors.parseDetail("\"detail\"")).isNull();
        assertThat(DownstreamErrors.parseDetail("\"detail\": 1")).isNull();
        assertThat(DownstreamErrors.parseDetail("\"detail\":\"")).isNull();
        assertThat(DownstreamErrors.parseDetail("\"detail\":\"   \"")).isNull();
        assertThat(DownstreamErrors.parseDetail("{\"detail\":\"Merchant inativo\"}")).isEqualTo("Merchant inativo");
    }

    @Test
    void choosesFallbackByStatus() {
        assertThat(DownstreamErrors.reserveFallback(HttpStatus.NOT_FOUND)).isEqualTo(FailureReasons.ACCOUNT_NOT_FOUND);
        assertThat(DownstreamErrors.reserveFallback(HttpStatus.UNPROCESSABLE_ENTITY)).isEqualTo(FailureReasons.INSUFFICIENT_FUNDS);
        assertThat(DownstreamErrors.reserveFallback(HttpStatus.BAD_REQUEST)).isEqualTo(FailureReasons.ORCHESTRATION_FAILED);
        assertThat(DownstreamErrors.merchantFallback(HttpStatus.NOT_FOUND)).isEqualTo(FailureReasons.MERCHANT_NOT_FOUND);
        assertThat(DownstreamErrors.merchantFallback(HttpStatus.UNPROCESSABLE_ENTITY)).isEqualTo(FailureReasons.MERCHANT_INACTIVE);
        assertThat(DownstreamErrors.merchantFallback(HttpStatus.CONFLICT)).isEqualTo(FailureReasons.ORCHESTRATION_FAILED);
    }

    private static HttpClientResponseException error(HttpStatus status, String body) {
        HttpResponse<String> response = body == null
                ? HttpResponse.status(status)
                : HttpResponse.status(status).body(body);
        return new HttpClientResponseException(status.getReason(), response);
    }
}
