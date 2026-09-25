package co.inter.piggies.reserve.web;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@MicronautTest(transactional = false)
class ReservationsHttpIT extends AbstractContainersTest {

    private static final String CLIENT_A_CPF = "12345678901";
    private static final String CLIENT_A_ACCOUNT = "000123";
    private static final String CLIENT_B_CPF = "98765432100";
    private static final String CLIENT_B_ACCOUNT = "000456";
    private static final String AGENCY = "0001";

    @Inject
    @Client("/")
    HttpClient http;

    @Test
    void createsWith201AndReturns200WhenRepeated() {
        UUID paymentId = UUID.randomUUID();

        HttpResponse<Map<String, Object>> created = exchange(put(paymentId, clientA("10")));
        HttpResponse<Map<String, Object>> repeated = exchange(put(paymentId, clientA("10")));

        assertThat(created.code()).isEqualTo(HttpStatus.CREATED.getCode());
        assertThat(created.body())
                .containsEntry("paymentId", paymentId.toString())
                .containsEntry("agency", AGENCY)
                .containsEntry("accountNumber", CLIENT_A_ACCOUNT)
                .containsEntry("amount", 10)
                .containsEntry("status", "RESERVED")
                .containsKeys("createdAt", "updatedAt");
        assertThat(repeated.code()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(repeated.body()).isEqualTo(created.body());
        assertThat(retrieve(HttpRequest.GET("/v1/reservations/" + paymentId))).isEqualTo(created.body());
    }

    @Test
    void sameIdWithOtherDataIsIdempotencyConflict() {
        UUID paymentId = UUID.randomUUID();
        exchange(put(paymentId, clientA("10")));

        assertProblem(put(paymentId, clientA("11")), HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
    }

    @Test
    void reservingMoreThanTheAvailableBalanceIs422() {
        assertProblem(put(UUID.randomUUID(), body(CLIENT_B_CPF, AGENCY, CLIENT_B_ACCOUNT, "51")),
                HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_BALANCE");
    }

    @Test
    void unknownPayerAccountIs422() {
        assertProblem(put(UUID.randomUUID(), body(CLIENT_A_CPF, AGENCY, "999999", "10")),
                HttpStatus.UNPROCESSABLE_ENTITY, "PAYER_ACCOUNT_NOT_FOUND");
    }

    @Test
    void confirmIsIdempotentAndReleaseAfterConfirmIsConflict() {
        UUID paymentId = UUID.randomUUID();
        exchange(put(paymentId, clientA("10")));

        Map<String, Object> confirmed = retrieve(post(paymentId, "confirm"));
        Map<String, Object> again = retrieve(post(paymentId, "confirm"));

        assertThat(confirmed).containsEntry("status", "CONFIRMED");
        assertThat(again).isEqualTo(confirmed);
        assertProblem(post(paymentId, "release"), HttpStatus.CONFLICT, "INVALID_RESERVATION_TRANSITION");
    }

    @Test
    void releaseIsIdempotentAndConfirmAfterReleaseIsConflict() {
        UUID paymentId = UUID.randomUUID();
        exchange(put(paymentId, clientA("10")));

        Map<String, Object> released = retrieve(post(paymentId, "release"));
        Map<String, Object> again = retrieve(post(paymentId, "release"));

        assertThat(released).containsEntry("status", "RELEASED");
        assertThat(again).isEqualTo(released);
        assertProblem(post(paymentId, "confirm"), HttpStatus.CONFLICT, "INVALID_RESERVATION_TRANSITION");
    }

    @Test
    void unknownReservationIsNotFound() {
        UUID paymentId = UUID.randomUUID();

        assertProblem(HttpRequest.GET("/v1/reservations/" + paymentId), HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND");
        assertProblem(post(paymentId, "confirm"), HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND");
        assertProblem(post(paymentId, "release"), HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "10.5", "\"100\"", "null"})
    void rejectsAmountThatIsNotAPositiveInteger(String amount) {
        UUID paymentId = UUID.randomUUID();

        assertProblem(put(paymentId, clientA(amount)), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertProblem(HttpRequest.GET("/v1/reservations/" + paymentId), HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND");
    }

    @Test
    void rejectsDocumentsOutOfFormat() {
        assertProblem(put(UUID.randomUUID(), body("123.456.789-01", AGENCY, CLIENT_A_ACCOUNT, "10")),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertProblem(put(UUID.randomUUID(), body(CLIENT_A_CPF, "1", CLIENT_A_ACCOUNT, "10")),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertProblem(put(UUID.randomUUID(), body(CLIENT_A_CPF, AGENCY, "12a", "10")),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    @Test
    void rejectsPaymentIdThatIsNotUuid() {
        assertProblem(HttpRequest.PUT("/v1/reservations/não-é-uuid", clientA("10"))
                .contentType(MediaType.APPLICATION_JSON_TYPE), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    @Test
    void accountShowsTheReservedBalance() {
        Map<String, Object> before = retrieve(HttpRequest.GET("/v1/accounts/" + AGENCY + "/" + CLIENT_B_ACCOUNT));

        exchange(put(UUID.randomUUID(), body(CLIENT_B_CPF, AGENCY, CLIENT_B_ACCOUNT, "5")));
        Map<String, Object> after = retrieve(HttpRequest.GET("/v1/accounts/" + AGENCY + "/" + CLIENT_B_ACCOUNT));

        assertThat(after)
                .containsEntry("agency", AGENCY)
                .containsEntry("accountNumber", CLIENT_B_ACCOUNT)
                .containsEntry("ownerCpf", CLIENT_B_CPF)
                .containsEntry("balance", before.get("balance"))
                .containsEntry("reservedBalance", (Integer) before.get("reservedBalance") + 5)
                .containsEntry("availableBalance", (Integer) before.get("availableBalance") - 5);
    }

    @Test
    void unknownAccountIsNotFound() {
        assertProblem(HttpRequest.GET("/v1/accounts/" + AGENCY + "/999999"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
    }

    @Test
    void rejectsAccountPathOutOfFormat() {
        assertProblem(HttpRequest.GET("/v1/accounts/1/" + CLIENT_A_ACCOUNT), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertProblem(HttpRequest.GET("/v1/accounts/" + AGENCY + "/12a"), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    private static String body(String cpf, String agency, String accountNumber, String amount) {
        return """
                {"cpf": "%s", "agency": "%s", "accountNumber": "%s", "amount": %s}
                """.formatted(cpf, agency, accountNumber, amount);
    }

    private static String clientA(String amount) {
        return body(CLIENT_A_CPF, AGENCY, CLIENT_A_ACCOUNT, amount);
    }

    private static HttpRequest<String> put(UUID paymentId, String body) {
        return HttpRequest.PUT("/v1/reservations/" + paymentId, body).contentType(MediaType.APPLICATION_JSON_TYPE);
    }

    private static HttpRequest<?> post(UUID paymentId, String action) {
        return HttpRequest.POST("/v1/reservations/" + paymentId + "/" + action, "");
    }

    private HttpResponse<Map<String, Object>> exchange(HttpRequest<?> request) {
        return http.toBlocking().exchange(request, Argument.mapOf(String.class, Object.class));
    }

    private Map<String, Object> retrieve(HttpRequest<?> request) {
        return http.toBlocking().retrieve(request, Argument.mapOf(String.class, Object.class));
    }

    private void assertProblem(HttpRequest<?> request, HttpStatus status, String code) {
        HttpClientResponseException error = catchThrowableOfType(HttpClientResponseException.class,
                () -> http.toBlocking().exchange(request));
        assertThat(error).as("esperava %s para %s %s", status, request.getPath(), request.getBody().orElse(null))
                .isNotNull();
        assertThat(error.getStatus().getCode()).isEqualTo(status.getCode());
        assertThat(error.getResponse().getContentType()).hasValue(MediaType.APPLICATION_JSON_PROBLEM_TYPE);
        assertThat(error.getResponse().getBody(Argument.mapOf(String.class, Object.class)))
                .hasValueSatisfying(problem -> assertThat(problem)
                        .containsEntry("code", code)
                        .containsEntry("status", status.getCode())
                        .containsKey("title"));
    }
}
