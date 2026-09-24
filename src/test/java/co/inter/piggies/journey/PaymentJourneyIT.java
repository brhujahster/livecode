package co.inter.piggies.journey;

import co.inter.piggies.coordinator.support.PaymentRequests;
import co.inter.piggies.merchant.facade.MerchantFacade;
import co.inter.piggies.reserve.facade.AccountView;
import co.inter.piggies.reserve.facade.ReservationStatus;
import co.inter.piggies.reserve.facade.ReserveFacade;
import co.inter.piggies.support.AbstractContainersTest;
import co.inter.piggies.support.TestKafka;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static co.inter.piggies.coordinator.support.PaymentRequests.AGENCY;
import static co.inter.piggies.coordinator.support.PaymentRequests.CLIENT_A_ACCOUNT;
import static co.inter.piggies.coordinator.support.PaymentRequests.CLIENT_B_ACCOUNT;
import static co.inter.piggies.coordinator.support.PaymentRequests.CLIENT_B_CPF;
import static co.inter.piggies.coordinator.support.PaymentRequests.MERCHANT_X;
import static co.inter.piggies.coordinator.support.PaymentRequests.MERCHANT_Y;
import static co.inter.piggies.coordinator.support.PaymentRequests.body;
import static co.inter.piggies.coordinator.support.PaymentRequests.clientAPays;
import static co.inter.piggies.coordinator.support.PaymentRequests.post;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cenários de aceite de US1, US2 e US3 de ponta a ponta, com os dados iniciais (Clientes A e B, Merchants X e Y).
 * O crédito e o fechamento passam pelo Kafka.
 */
@MicronautTest(transactional = false)
class PaymentJourneyIT extends AbstractContainersTest {

    @Inject
    @Client("/")
    HttpClient http;

    @Inject
    ReserveFacade reserve;

    @Inject
    MerchantFacade merchants;

    @Inject
    ObjectMapper json;

    @Value("${spp.topics.payment-confirmed}")
    String confirmedTopic;

    @Test
    void clientAPays100ToMerchantX() {
        AccountView before = account(CLIENT_A_ACCOUNT);
        long merchantBefore = merchants.findByCnpj(MERCHANT_X).orElseThrow().balance();

        UUID paymentId = pay(clientAPays(MERCHANT_X, 100));

        assertThat(awaitFinal(paymentId)).containsEntry("status", "CONFIRMED").doesNotContainKey("failureReason");
        AccountView after = account(CLIENT_A_ACCOUNT);
        assertThat(after.balance()).isEqualTo(before.balance() - 100);
        assertThat(after.reservedBalance()).isEqualTo(before.reservedBalance());
        assertThat(merchants.findReceivable(paymentId)).hasValueSatisfying(receivable -> {
            assertThat(receivable.amount()).isEqualTo(100);
            assertThat(receivable.merchantCnpj()).isEqualTo(MERCHANT_X);
        });
        assertThat(merchants.findByCnpj(MERCHANT_X).orElseThrow().balance()).isEqualTo(merchantBefore + 100);
        assertThat(confirmedEvents(paymentId)).singleElement().satisfies(event -> assertThat(event)
                .containsEntry("paymentId", paymentId.toString())
                .containsEntry("merchantCnpj", MERCHANT_X)
                .containsEntry("amount", 100)
                .containsEntry("eventVersion", 1)
                .containsKeys("eventId", "creditedAt"));
    }

    @Test
    void clientBWithoutBalanceFails() {
        AccountView before = account(CLIENT_B_ACCOUNT);

        UUID paymentId = pay(body(MERCHANT_X, "100", CLIENT_B_CPF, AGENCY, CLIENT_B_ACCOUNT));

        assertThat(awaitFinal(paymentId)).containsEntry("status", "FAILED").containsEntry("failureReason", "INSUFFICIENT_BALANCE");
        assertThat(account(CLIENT_B_ACCOUNT)).isEqualTo(before);
        assertThat(merchants.findReceivable(paymentId)).isEmpty();
        assertThat(confirmedEvents(paymentId)).isEmpty();
    }

    @Test
    void inactiveMerchantReleasesTheReservation() {
        AccountView before = account(CLIENT_A_ACCOUNT);

        UUID paymentId = pay(clientAPays(MERCHANT_Y, 100));

        assertThat(awaitFinal(paymentId)).containsEntry("status", "FAILED").containsEntry("failureReason", "MERCHANT_INACTIVE");
        assertThat(reserve.findReservation(paymentId))
                .hasValueSatisfying(reservation -> assertThat(reservation.status()).isEqualTo(ReservationStatus.RELEASED));
        assertThat(account(CLIENT_A_ACCOUNT).balance()).isEqualTo(before.balance());
        assertThat(account(CLIENT_A_ACCOUNT).availableBalance()).isEqualTo(before.availableBalance());
        assertThat(merchants.findReceivable(paymentId)).isEmpty();
        assertThat(confirmedEvents(paymentId)).isEmpty();
    }

    @Test
    void unknownMerchantFails() {
        AccountView before = account(CLIENT_A_ACCOUNT);

        UUID paymentId = pay(clientAPays("11111111000111", 100));

        assertThat(awaitFinal(paymentId)).containsEntry("status", "FAILED").containsEntry("failureReason", "MERCHANT_NOT_FOUND");
        assertThat(account(CLIENT_A_ACCOUNT).balance()).isEqualTo(before.balance());
        assertThat(account(CLIENT_A_ACCOUNT).availableBalance()).isEqualTo(before.availableBalance());
    }

    @Test
    void cpfThatDoesNotOwnTheAccountFails() {
        AccountView before = account(CLIENT_A_ACCOUNT);

        UUID paymentId = pay(body(MERCHANT_X, "100", CLIENT_B_CPF, AGENCY, CLIENT_A_ACCOUNT));

        assertThat(awaitFinal(paymentId)).containsEntry("status", "FAILED").containsEntry("failureReason", "PAYER_ACCOUNT_NOT_FOUND");
        assertThat(account(CLIENT_A_ACCOUNT)).isEqualTo(before);
    }

    private UUID pay(String body) {
        UUID paymentId = UUID.randomUUID();
        http.toBlocking().exchange(post(paymentId, body));
        return paymentId;
    }

    private List<Map<String, Object>> confirmedEvents(UUID paymentId) {
        return TestKafka.readAll(KAFKA.getBootstrapServers(), confirmedTopic).stream()
                .filter(record -> paymentId.toString().equals(record.key()))
                .map(record -> parse(record.value()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String event) {
        try {
            return json.readValue(event, Map.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, Object> awaitFinal(UUID paymentId) {
        return PaymentRequests.awaitFinal(http, paymentId);
    }

    private AccountView account(String number) {
        return reserve.findAccount(AGENCY, number).orElseThrow();
    }
}
