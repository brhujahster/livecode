package co.inter.piggies.journey;

import co.inter.piggies.coordinator.infra.messaging.PaymentDebited;
import co.inter.piggies.merchant.facade.MerchantFacade;
import co.inter.piggies.merchant.infra.messaging.PaymentConfirmed;
import co.inter.piggies.support.AbstractContainersTest;
import co.inter.piggies.support.TestKafka;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static co.inter.piggies.coordinator.support.PaymentRequests.AGENCY;
import static co.inter.piggies.coordinator.support.PaymentRequests.CLIENT_B_ACCOUNT;
import static co.inter.piggies.coordinator.support.PaymentRequests.CLIENT_B_CPF;
import static co.inter.piggies.coordinator.support.PaymentRequests.MERCHANT_X;
import static co.inter.piggies.coordinator.support.PaymentRequests.awaitFinal;
import static co.inter.piggies.coordinator.support.PaymentRequests.body;
import static co.inter.piggies.coordinator.support.PaymentRequests.clientAPays;
import static co.inter.piggies.coordinator.support.PaymentRequests.post;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * US4: o Kafka entrega pelo menos uma vez, então cada evento pode chegar repetido. A repetição não pode creditar
 * o merchant duas vezes nem mexer num pagamento já fechado.
 */
@MicronautTest(transactional = false)
class DuplicateEventsIT extends AbstractContainersTest {

    @Inject
    @Client("/")
    HttpClient http;

    @Inject
    MerchantFacade merchants;

    @Inject
    ObjectMapper json;

    @Value("${spp.topics.payment-debited}")
    String debitedTopic;

    @Value("${spp.topics.payment-confirmed}")
    String confirmedTopic;

    @Test
    void repeatedDebitedEventCreditsTheMerchantOnce() {
        long merchantBefore = merchants.findByCnpj(MERCHANT_X).orElseThrow().balance();
        UUID paymentId = UUID.randomUUID();
        Instant debitedAt = Instant.now();

        publishDebited(new PaymentDebited(UUID.randomUUID(), PaymentDebited.VERSION, paymentId, MERCHANT_X, 100, debitedAt));
        publishDebited(new PaymentDebited(UUID.randomUUID(), PaymentDebited.VERSION, paymentId, MERCHANT_X, 100, debitedAt));
        TestKafka.awaitConsumed(KAFKA.getBootstrapServers(), "spp-merchant", debitedTopic);

        assertThat(merchants.findByCnpj(MERCHANT_X).orElseThrow().balance()).isEqualTo(merchantBefore + 100);
        assertThat(merchants.findReceivable(paymentId)).hasValueSatisfying(receivable -> assertThat(receivable.amount()).isEqualTo(100));
        List<Map<String, Object>> confirmations = confirmedEvents(paymentId);
        assertThat(confirmations).as("cada entrega republica a confirmação, com o mesmo recebível").hasSize(2);
        assertThat(confirmations).extracting(event -> event.get("creditedAt")).containsOnly(confirmations.getFirst().get("creditedAt"));
    }

    @Test
    void repeatedConfirmedEventDoesNotChangeAClosedPayment() {
        UUID paymentId = UUID.randomUUID();
        http.toBlocking().exchange(post(paymentId, clientAPays(MERCHANT_X, 5)));
        Map<String, Object> confirmed = awaitFinal(http, paymentId);
        assertThat(confirmed).containsEntry("status", "CONFIRMED");

        publishConfirmed(paymentId, 5);
        TestKafka.awaitConsumed(KAFKA.getBootstrapServers(), "spp-coordinator", confirmedTopic);

        assertThat(payment(paymentId))
                .containsEntry("status", "CONFIRMED")
                .containsEntry("updatedAt", confirmed.get("updatedAt"));
    }

    @Test
    void confirmedEventForAFailedPaymentIsIgnored() {
        UUID paymentId = UUID.randomUUID();
        http.toBlocking().exchange(post(paymentId, body(MERCHANT_X, "100", CLIENT_B_CPF, AGENCY, CLIENT_B_ACCOUNT)));
        assertThat(awaitFinal(http, paymentId)).containsEntry("status", "FAILED");

        publishConfirmed(paymentId, 100);
        TestKafka.awaitConsumed(KAFKA.getBootstrapServers(), "spp-coordinator", confirmedTopic);

        assertThat(payment(paymentId))
                .containsEntry("status", "FAILED")
                .containsEntry("failureReason", "INSUFFICIENT_BALANCE");
    }

    private void publishDebited(PaymentDebited event) {
        TestKafka.publish(KAFKA.getBootstrapServers(), debitedTopic, event.paymentId().toString(), write(event));
    }

    private void publishConfirmed(UUID paymentId, long amount) {
        var event = new PaymentConfirmed(UUID.randomUUID(), PaymentConfirmed.VERSION, paymentId, MERCHANT_X, amount, Instant.now());
        TestKafka.publish(KAFKA.getBootstrapServers(), confirmedTopic, paymentId.toString(), write(event));
    }

    private Map<String, Object> payment(UUID paymentId) {
        return http.toBlocking().retrieve(HttpRequest.GET("/v1/payments/" + paymentId), Argument.mapOf(String.class, Object.class));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> confirmedEvents(UUID paymentId) {
        return TestKafka.readAll(KAFKA.getBootstrapServers(), confirmedTopic).stream()
                .filter(record -> paymentId.toString().equals(record.key()))
                .map(record -> {
                    try {
                        return (Map<String, Object>) json.readValue(record.value(), Map.class);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .toList();
    }

    private String write(Object event) {
        try {
            return json.writeValueAsString(event);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
