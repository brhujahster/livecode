package co.inter.piggies.coordinator.web;

import co.inter.piggies.coordinator.domain.FailureReason;
import co.inter.piggies.coordinator.domain.PaymentIntent;
import co.inter.piggies.coordinator.domain.PaymentService;
import co.inter.piggies.coordinator.domain.ReserveGateway;
import co.inter.piggies.coordinator.domain.Stage;
import co.inter.piggies.support.AbstractContainersTest;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static co.inter.piggies.coordinator.support.PaymentRequests.MERCHANT_X;
import static co.inter.piggies.coordinator.support.PaymentRequests.clientAPays;
import static co.inter.piggies.coordinator.support.PaymentRequests.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MicronautTest(transactional = false)
class PaymentsAsyncEdgeIT extends AbstractContainersTest {

    private final CountDownLatch reserveReleased = new CountDownLatch(1);
    private final CountDownLatch reserveStarted = new CountDownLatch(1);

    @Inject
    @Client("/")
    HttpClient http;

    @Inject
    PaymentService payments;

    @MockBean(ReserveGateway.class)
    ReserveGateway slowReserve() {
        return new ReserveGateway() {
            @Override
            public Optional<FailureReason> reserve(PaymentIntent intent) {
                reserveStarted.countDown();
                try {
                    reserveReleased.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Optional.of(FailureReason.INSUFFICIENT_BALANCE);
            }

            @Override
            public void confirm(UUID paymentId) {
            }

            @Override
            public void release(UUID paymentId) {
            }
        };
    }

    @AfterEach
    void releaseReserve() {
        reserveReleased.countDown();
    }

    @Test
    void respondsBeforeTheReservationFinishes() throws InterruptedException {
        UUID key = UUID.randomUUID();

        long start = System.nanoTime();
        HttpResponse<Map<String, Object>> response = http.toBlocking()
                .exchange(post(key, clientAPays(MERCHANT_X, 100)), Argument.mapOf(String.class, Object.class));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.code()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.body()).containsEntry("status", "PROCESSING");
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
        assertThat(reserveStarted.await(5, TimeUnit.SECONDS)).as("orquestração começou em segundo plano").isTrue();
        assertThat(payments.find(key).orElseThrow().getStage()).isEqualTo(Stage.ACCEPTED);

        reserveReleased.countDown();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(payments.find(key).orElseThrow().getStage()).isEqualTo(Stage.FAILED));
    }
}
