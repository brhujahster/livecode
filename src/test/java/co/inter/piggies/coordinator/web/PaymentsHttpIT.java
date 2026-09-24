package co.inter.piggies.coordinator.web;

import co.inter.piggies.support.AbstractContainersTest;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.UUID;

import static co.inter.piggies.coordinator.support.PaymentRequests.AGENCY;
import static co.inter.piggies.coordinator.support.PaymentRequests.CLIENT_A_ACCOUNT;
import static co.inter.piggies.coordinator.support.PaymentRequests.CLIENT_A_CPF;
import static co.inter.piggies.coordinator.support.PaymentRequests.MERCHANT_X;
import static co.inter.piggies.coordinator.support.PaymentRequests.body;
import static co.inter.piggies.coordinator.support.PaymentRequests.clientAPays;
import static co.inter.piggies.coordinator.support.PaymentRequests.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@MicronautTest(transactional = false)
class PaymentsHttpIT extends AbstractContainersTest {

    @Inject
    @Client("/")
    HttpClient http;

    @Test
    void acceptsPaymentWith202AndLocation() {
        UUID key = UUID.randomUUID();

        HttpResponse<Map<String, Object>> response = http.toBlocking()
                .exchange(post(key, clientAPays(MERCHANT_X, 10)), Argument.mapOf(String.class, Object.class));

        assertThat(response.code()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.header("Location")).isEqualTo("/v1/payments/" + key);
        assertThat(response.body()).containsEntry("paymentId", key.toString()).containsEntry("status", "PROCESSING");
    }

    @Test
    void returnsThePaymentById() {
        UUID key = UUID.randomUUID();
        http.toBlocking().exchange(post(key, clientAPays(MERCHANT_X, 10)));

        Map<String, Object> payment = http.toBlocking()
                .retrieve(HttpRequest.GET("/v1/payments/" + key), Argument.mapOf(String.class, Object.class));

        assertThat(payment).containsEntry("paymentId", key.toString()).containsKeys("status", "createdAt", "updatedAt");
        assertThat(payment.get("qrCode")).isEqualTo(Map.of("merchantCnpj", MERCHANT_X, "amount", 10));
        assertThat(payment.get("payer")).isEqualTo(Map.of("cpf", CLIENT_A_CPF, "agency", AGENCY, "accountNumber", CLIENT_A_ACCOUNT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "10.5", "\"100\"", "null"})
    void rejectsAmountThatIsNotAPositiveInteger(String amount) {
        UUID key = UUID.randomUUID();

        assertBadRequest(post(key, body(MERCHANT_X, amount, CLIENT_A_CPF, AGENCY, CLIENT_A_ACCOUNT)));
        assertNotFound(key);
    }

    @Test
    void rejectsDocumentsOutOfFormat() {
        assertBadRequest(post(UUID.randomUUID(), body("123", "10", CLIENT_A_CPF, AGENCY, CLIENT_A_ACCOUNT)));
        assertBadRequest(post(UUID.randomUUID(), body(MERCHANT_X, "10", "123.456.789-01", AGENCY, CLIENT_A_ACCOUNT)));
        assertBadRequest(post(UUID.randomUUID(), body(MERCHANT_X, "10", CLIENT_A_CPF, "1", CLIENT_A_ACCOUNT)));
    }

    @Test
    void rejectsMissingOrInvalidIdempotencyKey() {
        String body = clientAPays(MERCHANT_X, 10);

        assertBadRequest(HttpRequest.POST("/v1/payments", body).contentType(MediaType.APPLICATION_JSON_TYPE));
        assertBadRequest(HttpRequest.POST("/v1/payments", body).contentType(MediaType.APPLICATION_JSON_TYPE)
                .header("Idempotency-Key", "não-é-uuid"));
    }

    @Test
    void unknownPaymentIsProblemNotFound() {
        HttpClientResponseException error = catchThrowableOfType(HttpClientResponseException.class,
                () -> http.toBlocking().retrieve(HttpRequest.GET("/v1/payments/" + UUID.randomUUID())));

        assertThat(error.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(error.getResponse().getContentType()).hasValue(MediaType.APPLICATION_JSON_PROBLEM_TYPE);
        assertThat(error.getResponse().getBody(Argument.mapOf(String.class, Object.class)))
                .hasValueSatisfying(problem -> assertThat(problem)
                        .containsEntry("code", "PAYMENT_NOT_FOUND")
                        .containsEntry("status", 404)
                        .containsKeys("title", "detail"));
    }

    private void assertBadRequest(HttpRequest<?> request) {
        HttpClientResponseException error = catchThrowableOfType(HttpClientResponseException.class,
                () -> http.toBlocking().exchange(request));
        assertThat(error).as("esperava 400 para %s", request.getBody().orElse(null)).isNotNull();
        assertThat(error.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
        assertThat(error.getResponse().getContentType()).hasValue(MediaType.APPLICATION_JSON_PROBLEM_TYPE);
        assertThat(error.getResponse().getBody(Argument.mapOf(String.class, Object.class)))
                .hasValueSatisfying(problem -> assertThat(problem)
                        .containsEntry("code", "INVALID_REQUEST")
                        .containsEntry("status", 400)
                        .containsKeys("title", "detail"));
    }

    private void assertNotFound(UUID key) {
        HttpClientResponseException error = catchThrowableOfType(HttpClientResponseException.class,
                () -> http.toBlocking().retrieve(HttpRequest.GET("/v1/payments/" + key)));
        assertThat(error.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }
}
