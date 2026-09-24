package co.inter.piggies.coordinator;

import co.inter.piggies.coordinator.api.model.CreatePaymentRequest;
import co.inter.piggies.coordinator.api.model.PaymentAccepted;
import co.inter.piggies.coordinator.api.model.PaymentConfirmedEvent;
import co.inter.piggies.coordinator.api.model.PaymentIntent;
import co.inter.piggies.coordinator.api.model.PaymentStatus;
import co.inter.piggies.coordinator.domain.FailureReasons;
import co.inter.piggies.coordinator.messaging.PaymentConfirmedListener;
import co.inter.piggies.coordinator.orchestration.MerchantOutcome;
import co.inter.piggies.coordinator.orchestration.ReserveOutcome;
import co.inter.piggies.coordinator.support.CoordinatorScripts;
import co.inter.piggies.support.AbstractContainersTest;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@MicronautTest(transactional = false, environments = "coordinator-it")
class PaymentCoordinatorHttpTest extends AbstractContainersTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    PaymentConfirmedListener listener;

    @BeforeEach
    void resetScripts() {
        CoordinatorScripts.reset();
    }

    @Test
    @Timeout(10)
    void postReturnsAcceptedBeforeTheDebit() {
        var paymentId = UUID.randomUUID();
        CoordinatorScripts.reserve.gate = new CountDownLatch(1);
        try {
            HttpResponse<PaymentAccepted> response = post(paymentId, request(100L));

            assertThat(response.getStatus().getCode()).isEqualTo(202);
            assertThat(response.body().getPaymentId()).isEqualTo(paymentId);
            assertThat(response.body().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
            await().atMost(Duration.ofSeconds(3)).until(() -> CoordinatorScripts.reserve.entered.get() == 1);
            assertThat(CoordinatorScripts.reserve.finished.get()).isZero();
            assertThat(CoordinatorScripts.reserve.confirms.get()).isZero();
        } finally {
            CoordinatorScripts.reserve.gate.countDown();
        }
    }

    @Test
    void repeatsTheSamePaymentWithoutASecondReserve() {
        var paymentId = UUID.randomUUID();

        post(paymentId, request(100L));
        HttpResponse<PaymentAccepted> replay = post(paymentId, request(200L));

        assertThat(replay.getStatus().getCode()).isEqualTo(202);
        assertThat(replay.body().getPaymentId()).isEqualTo(paymentId);
        await().atMost(Duration.ofSeconds(5)).until(() -> CoordinatorScripts.reserve.finished.get() == 1);
        assertThat(CoordinatorScripts.reserve.entered.get()).isEqualTo(1);
        assertThat(get(paymentId).getAmount()).isEqualTo(100L);
    }

    @Test
    void confirmsThePaymentWhenTheCreditEventArrives() {
        var paymentId = UUID.randomUUID();
        post(paymentId, request(100L));
        await().atMost(Duration.ofSeconds(5)).until(() -> CoordinatorScripts.published.get() == 1);
        assertThat(get(paymentId).getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(CoordinatorScripts.reserve.confirms.get()).isEqualTo(1);

        listener.onConfirmed(new PaymentConfirmedEvent(
                paymentId,
                "12345678000199",
                100L,
                ZonedDateTime.now(ZoneOffset.UTC)
        ));

        PaymentIntent stored = get(paymentId);
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(stored.getReservationId()).isEqualTo(CoordinatorScripts.reserve.outcome.reservationId());
        assertThat(stored.getFailureReason()).isNull();
    }

    @Test
    void failsAndReleasesWhenTheMerchantIsInactive() {
        CoordinatorScripts.merchant.outcome = MerchantOutcome.rejected(FailureReasons.MERCHANT_INACTIVE);
        var paymentId = UUID.randomUUID();

        post(paymentId, request(100L));

        await().atMost(Duration.ofSeconds(5)).until(() -> get(paymentId).getStatus() == PaymentStatus.FAILED);
        assertThat(get(paymentId).getFailureReason()).isEqualTo(FailureReasons.MERCHANT_INACTIVE);
        assertThat(CoordinatorScripts.reserve.releases.get()).isEqualTo(1);
        assertThat(CoordinatorScripts.published.get()).isZero();
    }

    @Test
    void failsWithoutReleaseWhenTheBalanceIsInsufficient() {
        CoordinatorScripts.reserve.outcome = ReserveOutcome.rejected(FailureReasons.INSUFFICIENT_FUNDS);
        var paymentId = UUID.randomUUID();

        post(paymentId, request(100L));

        await().atMost(Duration.ofSeconds(5)).until(() -> get(paymentId).getStatus() == PaymentStatus.FAILED);
        assertThat(get(paymentId).getFailureReason()).isEqualTo(FailureReasons.INSUFFICIENT_FUNDS);
        assertThat(CoordinatorScripts.reserve.releases.get()).isZero();
        assertThat(CoordinatorScripts.reserve.confirms.get()).isZero();
    }

    @Test
    void rejectsAnInvalidAmountAndAnUnknownPayment() {
        var missingKey = HttpRequest.POST("/v1/payments", request(100L));
        assertThatThrownBy(() -> client.toBlocking().exchange(missingKey, PaymentAccepted.class))
                .isInstanceOf(HttpClientResponseException.class)
                .extracting(exception -> ((HttpClientResponseException) exception).getStatus().getCode())
                .isEqualTo(400);

        var invalidAmount = HttpRequest.POST("/v1/payments", """
                        {"payerCpf":"12345678901","payerAgency":"0001","payerAccount":"000123","merchantCnpj":"12345678000199","amount":0}
                        """)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .header("Idempotency-Key", UUID.randomUUID().toString());
        assertThatThrownBy(() -> client.toBlocking().exchange(invalidAmount, PaymentAccepted.class))
                .isInstanceOf(HttpClientResponseException.class)
                .extracting(exception -> ((HttpClientResponseException) exception).getStatus().getCode())
                .isEqualTo(400);

        var fractional = HttpRequest.POST("/v1/payments", """
                        {"payerCpf":"12345678901","payerAgency":"0001","payerAccount":"000123","merchantCnpj":"12345678000199","amount":10.5}
                        """)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .header("Idempotency-Key", UUID.randomUUID().toString());
        assertThatThrownBy(() -> client.toBlocking().exchange(fractional, PaymentAccepted.class))
                .isInstanceOf(HttpClientResponseException.class)
                .extracting(exception -> ((HttpClientResponseException) exception).getStatus().getCode())
                .isEqualTo(400);

        assertThatThrownBy(() -> get(UUID.randomUUID()))
                .isInstanceOf(HttpClientResponseException.class)
                .extracting(exception -> ((HttpClientResponseException) exception).getStatus().getCode())
                .isEqualTo(404);
        assertThat(CoordinatorScripts.reserve.entered.get()).isZero();
    }

    private HttpResponse<PaymentAccepted> post(UUID paymentId, Object body) {
        var request = HttpRequest.POST("/v1/payments", body)
                .header("Idempotency-Key", paymentId.toString());
        return client.toBlocking().exchange(request, PaymentAccepted.class);
    }

    private PaymentIntent get(UUID paymentId) {
        return client.toBlocking().retrieve(HttpRequest.GET("/v1/payments/" + paymentId), PaymentIntent.class);
    }

    private static CreatePaymentRequest request(Long amount) {
        return new CreatePaymentRequest("12345678901", "0001", "000123", "12345678000199", amount);
    }
}
