package co.inter.piggies.coordinator.client;

import co.inter.piggies.coordinator.client.merchant.MerchantsApi;
import co.inter.piggies.coordinator.client.merchant.model.CreateCreditRequest;
import co.inter.piggies.coordinator.client.merchant.model.Merchant;
import co.inter.piggies.coordinator.client.merchant.model.MerchantStatus;
import co.inter.piggies.coordinator.client.merchant.model.Receivable;
import co.inter.piggies.coordinator.client.merchant.model.ValidateMerchantRequest;
import co.inter.piggies.coordinator.client.reserve.ReservationsApi;
import co.inter.piggies.coordinator.client.reserve.model.CreateReservationRequest;
import co.inter.piggies.coordinator.client.reserve.model.Reservation;
import co.inter.piggies.coordinator.client.reserve.model.ReservationStatus;
import co.inter.piggies.coordinator.domain.FailureReasons;
import co.inter.piggies.coordinator.domain.PaymentIntentEntity;
import co.inter.piggies.coordinator.orchestration.MerchantOutcome;
import co.inter.piggies.coordinator.orchestration.ReserveOutcome;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayAdapterTest {

    private final UUID paymentId = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private final UUID reservationId = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    @Test
    void mapsACreatedReservation() {
        var api = new FakeReservations(HttpResponse.created(reservation()));
        var adapter = new ReserveGatewayAdapter(api);

        ReserveOutcome outcome = adapter.reserve(intent());

        assertThat(outcome.succeeded()).isTrue();
        assertThat(outcome.reservationId()).isEqualTo(reservationId);
        assertThat(api.lastRequest.getPaymentIntentId()).isEqualTo(paymentId);
        assertThat(api.lastRequest.getAmount()).isEqualTo(100L);
    }

    @Test
    void rejectsAnEmptyReservationBody() {
        var adapter = new ReserveGatewayAdapter(new FakeReservations(HttpResponse.ok()));

        assertThat(adapter.reserve(intent()).failureReason()).isEqualTo(FailureReasons.ORCHESTRATION_FAILED);
    }

    @Test
    void mapsReserveRejectionsAndPropagatesUnexpectedStatus() {
        var insufficient = new ReserveGatewayAdapter(new FakeReservations(error(HttpStatus.UNPROCESSABLE_ENTITY, "{\"detail\":\"Saldo insuficiente\"}")));
        assertThat(insufficient.reserve(intent()).failureReason()).isEqualTo("Saldo insuficiente");

        var missing = new ReserveGatewayAdapter(new FakeReservations(error(HttpStatus.NOT_FOUND, null)));
        assertThat(missing.reserve(intent()).failureReason()).isEqualTo(FailureReasons.ACCOUNT_NOT_FOUND);

        var invalid = new ReserveGatewayAdapter(new FakeReservations(error(HttpStatus.BAD_REQUEST, null)));
        assertThat(invalid.reserve(intent()).failureReason()).isEqualTo(FailureReasons.ORCHESTRATION_FAILED);

        var conflict = new ReserveGatewayAdapter(new FakeReservations(error(HttpStatus.CONFLICT, null)));
        assertThat(conflict.reserve(intent()).failureReason()).isEqualTo(FailureReasons.ORCHESTRATION_FAILED);

        var down = new ReserveGatewayAdapter(new FakeReservations(error(HttpStatus.INTERNAL_SERVER_ERROR, null)));
        assertThatThrownBy(() -> down.reserve(intent())).isInstanceOf(HttpClientResponseException.class);
    }

    @Test
    void confirmsAndReleasesByReservationId() {
        var api = new FakeReservations(HttpResponse.ok(reservation()));
        var adapter = new ReserveGatewayAdapter(api);

        adapter.confirm(reservationId);
        adapter.release(reservationId);

        assertThat(api.confirmed).isEqualTo(reservationId);
        assertThat(api.released).isEqualTo(reservationId);
    }

    @Test
    void acceptsOnlyAnActiveMerchant() {
        var active = new MerchantGatewayAdapter(new FakeMerchants(merchant(MerchantStatus.ACTIVE), null));
        assertThat(active.validate("12345678000199")).isEqualTo(MerchantOutcome.accepted());

        var inactive = new MerchantGatewayAdapter(new FakeMerchants(merchant(MerchantStatus.INACTIVE), null));
        assertThat(inactive.validate("12345678000199").failureReason()).isEqualTo(FailureReasons.MERCHANT_INACTIVE);

        var empty = new MerchantGatewayAdapter(new FakeMerchants(null, null));
        assertThat(empty.validate("12345678000199").failureReason()).isEqualTo(FailureReasons.MERCHANT_INACTIVE);
    }

    @Test
    void mapsMerchantRejectionsAndPropagatesUnexpectedStatus() {
        var missing = new MerchantGatewayAdapter(new FakeMerchants(null, error(HttpStatus.NOT_FOUND, null)));
        assertThat(missing.validate("12345678000199").failureReason()).isEqualTo(FailureReasons.MERCHANT_NOT_FOUND);

        var inactive = new MerchantGatewayAdapter(new FakeMerchants(null, error(HttpStatus.UNPROCESSABLE_ENTITY, "{\"detail\":\"Merchant inativo\"}")));
        assertThat(inactive.validate("12345678000199").failureReason()).isEqualTo("Merchant inativo");

        var down = new MerchantGatewayAdapter(new FakeMerchants(null, error(HttpStatus.SERVICE_UNAVAILABLE, null)));
        assertThatThrownBy(() -> down.validate("12345678000199")).isInstanceOf(HttpClientResponseException.class);
    }

    private PaymentIntentEntity intent() {
        return PaymentIntentEntity.start(paymentId, "12345678901", "0001", "000123", "12345678000199", 100L, Instant.parse("2026-09-24T19:46:00Z"));
    }

    private Reservation reservation() {
        return new Reservation(reservationId, paymentId, UUID.randomUUID(), 100L, ReservationStatus.RESERVED, ZonedDateTime.now(ZoneOffset.UTC));
    }

    private Merchant merchant(MerchantStatus status) {
        return new Merchant(UUID.randomUUID(), "Merchant X", "12345678000199", "0001", "000999", status);
    }

    private static HttpClientResponseException error(HttpStatus status, String body) {
        HttpResponse<String> response = body == null ? HttpResponse.status(status) : HttpResponse.status(status).body(body);
        return new HttpClientResponseException(status.getReason(), response);
    }

    private static final class FakeReservations implements ReservationsApi {
        private final HttpResponse<Reservation> response;
        private final HttpClientResponseException error;
        private CreateReservationRequest lastRequest;
        private UUID confirmed;
        private UUID released;

        private FakeReservations(HttpResponse<Reservation> response) {
            this.response = response;
            this.error = null;
        }

        private FakeReservations(HttpClientResponseException error) {
            this.response = null;
            this.error = error;
        }

        @Override
        public Reservation confirmReservation(UUID id) {
            confirmed = id;
            return null;
        }

        @Override
        public HttpResponse<Reservation> createReservation(CreateReservationRequest createReservationRequest) {
            lastRequest = createReservationRequest;
            if (error != null) {
                throw error;
            }
            return response;
        }

        @Override
        public Reservation releaseReservation(UUID id) {
            released = id;
            return null;
        }
    }

    private static final class FakeMerchants implements MerchantsApi {
        private final Merchant merchant;
        private final HttpClientResponseException error;

        private FakeMerchants(Merchant merchant, HttpClientResponseException error) {
            this.merchant = merchant;
            this.error = error;
        }

        @Override
        public HttpResponse<Receivable> createCredit(CreateCreditRequest createCreditRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Merchant validateMerchant(ValidateMerchantRequest validateMerchantRequest) {
            if (error != null) {
                throw error;
            }
            return merchant;
        }
    }
}
