package co.inter.piggies;

import co.inter.piggies.dto.CreatePaymentRequest;
import co.inter.piggies.dto.PaymentAccepted;
import co.inter.piggies.event.PaymentConfirmedEvent;
import co.inter.piggies.model.*;
import co.inter.piggies.repository.*;
import co.inter.piggies.support.AbstractContainersTest;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetReset;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MicronautTest(transactional = false)
public class PiggiesIntegrationTest extends AbstractContainersTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Inject
    ClientRepository clientRepository;

    @Inject
    AccountRepository accountRepository;

    @Inject
    MerchantRepository merchantRepository;

    @Inject
    ReservationRepository reservationRepository;

    @Inject
    ReceivableRepository receivableRepository;

    @Inject
    PaymentIntentRepository paymentIntentRepository;

    @Inject
    TestConfirmedEventListener testConfirmedEventListener;

    @BeforeEach
    void setUp() {
        testConfirmedEventListener.events.clear();
        receivableRepository.deleteAll();
        reservationRepository.deleteAll();
        paymentIntentRepository.deleteAll();
        accountRepository.deleteAll();
        clientRepository.deleteAll();
        merchantRepository.deleteAll();
    }

    @Test
    void testEndToEndPaymentSuccess() {
        // Arrange: Cliente A com saldo 500 e reservedBalance 0
        co.inter.piggies.model.Client clientA = clientRepository.save(
                co.inter.piggies.model.Client.builder()
                        .name("Cliente A")
                        .cpf("12345678901")
                        .build()
        );

        Account accountA = accountRepository.save(
                Account.builder()
                        .clientId(clientA.getId())
                        .agency("0001")
                        .number("000123")
                        .balance(500L)
                        .reservedBalance(0L)
                        .build()
        );

        // Merchant X ativo
        Merchant merchantX = merchantRepository.save(
                Merchant.builder()
                        .name("Merchant X")
                        .cnpj("12345678000199")
                        .agency("0001")
                        .accountNumber("000999")
                        .status(MerchantStatus.ACTIVE)
                        .build()
        );

        UUID paymentId = UUID.randomUUID();
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .payerCpf("12345678901")
                .payerAgency("0001")
                .payerAccount("000123")
                .merchantCnpj("12345678000199")
                .amount(100L)
                .build();

        // Act: POST /v1/payments
        HttpRequest<CreatePaymentRequest> postRequest = HttpRequest.POST("/v1/payments", request)
                .header("Idempotency-Key", paymentId.toString());

        HttpResponse<PaymentAccepted> response = httpClient.toBlocking().exchange(postRequest, PaymentAccepted.class);
        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(response.body());
        org.junit.jupiter.api.Assertions.assertEquals(paymentId, response.body().getPaymentId());

        // Assert: Aguardar processamento assíncrono via Kafka e fechamento da intenção
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<PaymentIntent> intentOpt = paymentIntentRepository.findById(paymentId);
            assertThat(intentOpt).isPresent();
            PaymentIntent intent = intentOpt.get();
            assertThat(intent.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        });

        // Validar saldo da conta do Cliente A
        Account updatedAccount = accountRepository.findById(accountA.getId()).orElseThrow();
        assertThat(updatedAccount.getBalance()).isEqualTo(400L); // 500 - 100
        assertThat(updatedAccount.getReservedBalance()).isEqualTo(0L); // de volta a 0

        // Validar recebível criado para Merchant X
        Optional<Receivable> receivableOpt = receivableRepository.findByPaymentIntentId(paymentId);
        assertThat(receivableOpt).isPresent();
        Receivable receivable = receivableOpt.get();
        assertThat(receivable.getMerchantId()).isEqualTo(merchantX.getId());
        assertThat(receivable.getAmount()).isEqualTo(100L);
        assertThat(receivable.getStatus()).isEqualTo(ReceivableStatus.CREDITED);
        assertThat(receivable.getCreditedAt()).isNotNull();

        // Validar mensagem em spp.payment.confirmed
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(testConfirmedEventListener.events).anyMatch(e ->
                    e.getPaymentId().equals(paymentId) &&
                    e.getMerchantCnpj().equals("12345678000199") &&
                    e.getAmount().equals(100L) &&
                    e.getCreditedAt() != null
            );
        });
    }

    @Test
    void testPaymentFailsWhenMerchantInactive() {
        // Arrange: Cliente A com saldo 500 e reservedBalance 0
        co.inter.piggies.model.Client clientA = clientRepository.save(
                co.inter.piggies.model.Client.builder()
                        .name("Cliente A")
                        .cpf("12345678901")
                        .build()
        );

        Account accountA = accountRepository.save(
                Account.builder()
                        .clientId(clientA.getId())
                        .agency("0001")
                        .number("000123")
                        .balance(500L)
                        .reservedBalance(0L)
                        .build()
        );

        // Merchant inativo
        Merchant merchantInactive = merchantRepository.save(
                Merchant.builder()
                        .name("Merchant Inactive")
                        .cnpj("99999999000199")
                        .agency("0001")
                        .accountNumber("000888")
                        .status(MerchantStatus.INACTIVE)
                        .build()
        );

        UUID paymentId = UUID.randomUUID();
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .payerCpf("12345678901")
                .payerAgency("0001")
                .payerAccount("000123")
                .merchantCnpj("99999999000199")
                .amount(100L)
                .build();

        // Act: POST /v1/payments
        HttpRequest<CreatePaymentRequest> postRequest = HttpRequest.POST("/v1/payments", request)
                .header("Idempotency-Key", paymentId.toString());

        HttpResponse<PaymentAccepted> response = httpClient.toBlocking().exchange(postRequest, PaymentAccepted.class);
        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.ACCEPTED, response.getStatus());

        // Assert: Intenção termina FAILED
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<PaymentIntent> intentOpt = paymentIntentRepository.findById(paymentId);
            assertThat(intentOpt).isPresent();
            PaymentIntent intent = intentOpt.get();
            assertThat(intent.getStatus()).isEqualTo(PaymentStatus.FAILED);
        });

        // Reserva RELEASED
        Optional<Reservation> reservationOpt = reservationRepository.findByPaymentIntentId(paymentId);
        assertThat(reservationOpt).isPresent();
        assertThat(reservationOpt.get().getStatus()).isEqualTo(ReservationStatus.RELEASED);

        // Nenhum recebível criado
        Optional<Receivable> receivableOpt = receivableRepository.findByPaymentIntentId(paymentId);
        assertThat(receivableOpt).isEmpty();

        // Nenhum débito (saldo permanece 500, reservedBalance 0)
        Account updatedAccount = accountRepository.findById(accountA.getId()).orElseThrow();
        assertThat(updatedAccount.getBalance()).isEqualTo(500L);
        assertThat(updatedAccount.getReservedBalance()).isEqualTo(0L);
    }

    @Singleton
    @KafkaListener(offsetReset = OffsetReset.EARLIEST, groupId = "test-confirmed-listener")
    public static class TestConfirmedEventListener {
        public final List<PaymentConfirmedEvent> events = new CopyOnWriteArrayList<>();

        @Topic("spp.payment.confirmed")
        public void onPaymentConfirmed(PaymentConfirmedEvent event) {
            events.add(event);
        }
    }
}
