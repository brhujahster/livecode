package co.inter.piggies.coordinator.support;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

public final class PaymentRequests {

    public static final String CLIENT_A_CPF = "12345678901";
    public static final String CLIENT_A_ACCOUNT = "000123";
    public static final String CLIENT_B_CPF = "98765432100";
    public static final String CLIENT_B_ACCOUNT = "000456";
    public static final String AGENCY = "0001";
    public static final String MERCHANT_X = "12345678000199";
    public static final String MERCHANT_Y = "98765432000155";

    private PaymentRequests() {
    }

    public static String body(String merchantCnpj, String amount, String cpf, String agency, String accountNumber) {
        return """
                {"qrCode": {"merchantCnpj": "%s", "amount": %s},
                 "payer": {"cpf": "%s", "agency": "%s", "accountNumber": "%s"}}
                """.formatted(merchantCnpj, amount, cpf, agency, accountNumber);
    }

    public static String clientAPays(String merchantCnpj, long amount) {
        return body(merchantCnpj, Long.toString(amount), CLIENT_A_CPF, AGENCY, CLIENT_A_ACCOUNT);
    }

    public static HttpRequest<String> post(UUID idempotencyKey, String body) {
        return HttpRequest.POST("/v1/payments", body)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .header("Idempotency-Key", idempotencyKey.toString());
    }

    /**
     * Consulta o pagamento até ele sair de {@code PROCESSING}.
     */
    public static Map<String, Object> awaitFinal(HttpClient http, UUID paymentId) {
        return await().atMost(Duration.ofSeconds(20)).until(
                () -> http.toBlocking().retrieve(HttpRequest.GET("/v1/payments/" + paymentId), Argument.mapOf(String.class, Object.class)),
                payment -> !"PROCESSING".equals(payment.get("status")));
    }
}
