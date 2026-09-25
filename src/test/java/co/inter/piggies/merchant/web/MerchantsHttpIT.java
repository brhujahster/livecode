package co.inter.piggies.merchant.web;

import co.inter.piggies.merchant.facade.CreditCommand;
import co.inter.piggies.merchant.facade.MerchantFacade;
import co.inter.piggies.merchant.facade.ReceivableView;
import co.inter.piggies.support.AbstractContainersTest;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@MicronautTest(transactional = false)
class MerchantsHttpIT extends AbstractContainersTest {

    private static final String MERCHANT_X = "12345678000199";
    private static final String MERCHANT_Y = "98765432000155";

    @Inject
    @Client("/")
    HttpClient http;

    @Inject
    MerchantFacade merchants;

    @Test
    void returnsTheMerchantWithItsAccount() {
        Map<String, Object> merchant = retrieve("/v1/merchants/" + MERCHANT_X);

        assertThat(merchant)
                .containsEntry("cnpj", MERCHANT_X)
                .containsEntry("name", "Merchant X")
                .containsEntry("status", "ACTIVE");
        assertThat(merchant.get("account")).isInstanceOfSatisfying(Map.class, account -> assertThat(account)
                .containsEntry("agency", "0001")
                .containsEntry("accountNumber", "900001")
                .containsKey("balance"));
    }

    @Test
    void inactiveMerchantIsStillReturned() {
        assertThat(retrieve("/v1/merchants/" + MERCHANT_Y)).containsEntry("status", "INACTIVE");
    }

    @Test
    void unknownMerchantIsNotFound() {
        assertProblem("/v1/merchants/00000000000000", HttpStatus.NOT_FOUND, "MERCHANT_NOT_FOUND");
    }

    @Test
    void rejectsCnpjOutOfFormat() {
        assertProblem("/v1/merchants/12.345.678-0001-99", HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertProblem("/v1/merchants/123", HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    @Test
    void returnsTheCreditedReceivable() {
        UUID paymentId = UUID.randomUUID();
        ReceivableView credited = merchants.credit(new CreditCommand(paymentId, MERCHANT_X, 25));

        Map<String, Object> receivable = retrieve("/v1/receivables/" + paymentId);

        assertThat(receivable)
                .containsEntry("paymentId", paymentId.toString())
                .containsEntry("merchantCnpj", MERCHANT_X)
                .containsEntry("amount", 25)
                .containsEntry("status", "CREDITED");
        assertThat(Instant.parse((String) receivable.get("creditedAt"))).isEqualTo(credited.creditedAt());
    }

    @Test
    void unknownReceivableIsNotFound() {
        assertProblem("/v1/receivables/" + UUID.randomUUID(), HttpStatus.NOT_FOUND, "RECEIVABLE_NOT_FOUND");
    }

    @Test
    void rejectsReceivableIdThatIsNotUuid() {
        assertProblem("/v1/receivables/não-é-uuid", HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    private Map<String, Object> retrieve(String path) {
        return http.toBlocking().retrieve(HttpRequest.GET(path), Argument.mapOf(String.class, Object.class));
    }

    private void assertProblem(String path, HttpStatus status, String code) {
        HttpClientResponseException error = catchThrowableOfType(HttpClientResponseException.class,
                () -> http.toBlocking().exchange(HttpRequest.GET(path)));
        assertThat(error).as("esperava %s para %s", status, path).isNotNull();
        assertThat(error.getStatus().getCode()).isEqualTo(status.getCode());
        assertThat(error.getResponse().getContentType()).hasValue(MediaType.APPLICATION_JSON_PROBLEM_TYPE);
        assertThat(error.getResponse().getBody(Argument.mapOf(String.class, Object.class)))
                .hasValueSatisfying(problem -> assertThat(problem)
                        .containsEntry("code", code)
                        .containsEntry("status", status.getCode())
                        .containsKey("title"));
    }
}
