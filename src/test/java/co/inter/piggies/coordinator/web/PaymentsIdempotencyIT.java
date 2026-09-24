package co.inter.piggies.coordinator.web;

import co.inter.piggies.merchant.facade.MerchantFacade;
import co.inter.piggies.reserve.facade.ReserveFacade;
import co.inter.piggies.support.AbstractContainersTest;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static co.inter.piggies.coordinator.support.PaymentRequests.AGENCY;
import static co.inter.piggies.coordinator.support.PaymentRequests.CLIENT_A_ACCOUNT;
import static co.inter.piggies.coordinator.support.PaymentRequests.MERCHANT_X;
import static co.inter.piggies.coordinator.support.PaymentRequests.awaitFinal;
import static co.inter.piggies.coordinator.support.PaymentRequests.clientAPays;
import static co.inter.piggies.coordinator.support.PaymentRequests.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * US4: a Idempotency-Key é o id do pagamento. Repetir o pedido não cobra duas vezes; reaproveitar a chave com
 * outros dados é conflito.
 */
@MicronautTest(transactional = false)
class PaymentsIdempotencyIT extends AbstractContainersTest {

    @Inject
    @Client("/")
    HttpClient http;

    @Inject
    ReserveFacade reserve;

    @Inject
    MerchantFacade merchants;

    @Test
    void sameKeyAndSameDataReturnTheSamePaymentAndChargeOnce() {
        long balanceBefore = clientABalance();
        UUID key = UUID.randomUUID();

        HttpResponse<Map<String, Object>> first = send(key, clientAPays(MERCHANT_X, 7));
        awaitFinal(http, key);
        HttpResponse<Map<String, Object>> repeated = send(key, clientAPays(MERCHANT_X, 7));

        assertThat(first.code()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(repeated.code()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(repeated.body()).containsEntry("paymentId", key.toString()).containsEntry("status", "CONFIRMED");
        assertThat(clientABalance()).isEqualTo(balanceBefore - 7);
        assertThat(merchants.findReceivable(key)).hasValueSatisfying(receivable -> assertThat(receivable.amount()).isEqualTo(7));
    }

    @Test
    void sameKeyWithDifferentDataIsConflict() {
        UUID key = UUID.randomUUID();
        send(key, clientAPays(MERCHANT_X, 10));

        HttpClientResponseException error = catchThrowableOfType(HttpClientResponseException.class,
                () -> send(key, clientAPays(MERCHANT_X, 11)));

        assertThat(error).isNotNull();
        assertThat(error.getStatus().getCode()).isEqualTo(HttpStatus.CONFLICT.getCode());
        assertThat(error.getResponse().getContentType()).hasValue(MediaType.APPLICATION_JSON_PROBLEM_TYPE);
        assertThat(error.getResponse().getBody(Argument.mapOf(String.class, Object.class)))
                .hasValueSatisfying(problem -> assertThat(problem)
                        .containsEntry("code", "IDEMPOTENCY_CONFLICT")
                        .containsEntry("status", 409)
                        .containsKeys("title", "detail"));
        assertThat(awaitFinal(http, key).get("qrCode")).isEqualTo(Map.of("merchantCnpj", MERCHANT_X, "amount", 10));
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateOnePayment() throws Exception {
        long balanceBefore = clientABalance();
        UUID key = UUID.randomUUID();
        String body = clientAPays(MERCHANT_X, 3);
        CountDownLatch start = new CountDownLatch(1);

        List<Integer> statuses;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> responses = IntStream.range(0, 10)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return send(key, body).code();
                    }))
                    .toList();
            start.countDown();
            statuses = responses.stream().map(PaymentsIdempotencyIT::join).toList();
        }

        assertThat(statuses).containsOnly(HttpStatus.ACCEPTED.getCode()).hasSize(10);
        assertThat(awaitFinal(http, key)).containsEntry("status", "CONFIRMED");
        assertThat(clientABalance()).isEqualTo(balanceBefore - 3);
    }

    private HttpResponse<Map<String, Object>> send(UUID key, String body) {
        return http.toBlocking().exchange(post(key, body), Argument.mapOf(String.class, Object.class));
    }

    private long clientABalance() {
        return reserve.findAccount(AGENCY, CLIENT_A_ACCOUNT).orElseThrow().balance();
    }

    private static <T> T join(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
