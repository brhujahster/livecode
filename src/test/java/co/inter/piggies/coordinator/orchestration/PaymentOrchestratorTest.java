package co.inter.piggies.coordinator.orchestration;

import co.inter.piggies.coordinator.domain.FailureReason;
import co.inter.piggies.coordinator.domain.MerchantGateway;
import co.inter.piggies.coordinator.domain.NewPayment;
import co.inter.piggies.coordinator.domain.PaymentIntent;
import co.inter.piggies.coordinator.domain.PaymentService;
import co.inter.piggies.coordinator.domain.PaymentStatus;
import co.inter.piggies.coordinator.domain.ReserveGateway;
import co.inter.piggies.coordinator.domain.Stage;
import co.inter.piggies.coordinator.support.InMemoryPaymentIntentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentOrchestratorTest {

    private static final String CNPJ = "12345678000199";

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final PaymentService payments = new PaymentService(new InMemoryPaymentIntentStore());
    private final FakeReserve reserve = new FakeReserve();
    private final FakeMerchant merchant = new FakeMerchant();
    private final PaymentOrchestrator orchestrator = new PaymentOrchestrator(payments, reserve, merchant, executor);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void confirmsWhenReserveAndMerchantSucceed() {
        UUID id = accept();

        orchestrator.process(id);

        assertThat(intent(id).status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(reserve.calls).containsExactly("reserve", "confirm");
        assertThat(merchant.credits).containsExactly(id);
    }

    @Test
    void insufficientBalanceFailsWithoutRelease() {
        reserve.failure = Optional.of(FailureReason.INSUFFICIENT_BALANCE);
        UUID id = accept();

        orchestrator.process(id);

        assertThat(intent(id).status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(intent(id).getFailureReason()).isEqualTo(FailureReason.INSUFFICIENT_BALANCE);
        assertThat(reserve.calls).containsExactly("reserve");
        assertThat(merchant.credits).isEmpty();
    }

    @Test
    void inactiveMerchantReleasesReservationAndFails() {
        merchant.failure = Optional.of(FailureReason.MERCHANT_INACTIVE);
        UUID id = accept();

        orchestrator.process(id);

        assertThat(intent(id).getFailureReason()).isEqualTo(FailureReason.MERCHANT_INACTIVE);
        assertThat(reserve.calls).containsExactly("reserve", "release");
        assertThat(merchant.credits).isEmpty();
    }

    @Test
    void unknownMerchantReleasesReservationAndFails() {
        merchant.failure = Optional.of(FailureReason.MERCHANT_NOT_FOUND);
        UUID id = accept();

        orchestrator.process(id);

        assertThat(intent(id).getFailureReason()).isEqualTo(FailureReason.MERCHANT_NOT_FOUND);
        assertThat(reserve.calls).containsExactly("reserve", "release");
    }

    @Test
    void whenBothFailTheReserveReasonWins() {
        reserve.failure = Optional.of(FailureReason.PAYER_ACCOUNT_NOT_FOUND);
        merchant.failure = Optional.of(FailureReason.MERCHANT_INACTIVE);
        UUID id = accept();

        orchestrator.process(id);

        assertThat(intent(id).getFailureReason()).isEqualTo(FailureReason.PAYER_ACCOUNT_NOT_FOUND);
        assertThat(reserve.calls).containsExactly("reserve");
    }

    @Test
    void technicalFailureKeepsThePaymentAccepted() {
        reserve.explode = true;
        UUID id = accept();

        orchestrator.process(id);

        assertThat(intent(id).getStage()).isEqualTo(Stage.ACCEPTED);
        assertThat(merchant.credits).isEmpty();
    }

    @Test
    void technicalFailureOnCreditKeepsThePaymentDebited() {
        merchant.explodeOnCredit = true;
        UUID id = accept();

        orchestrator.process(id);

        assertThat(intent(id).getStage()).isEqualTo(Stage.DEBITED);
    }

    @Test
    void reserveAndMerchantValidationRunInParallel() {
        CyclicBarrier bothRunning = new CyclicBarrier(2);
        reserve.barrier = bothRunning;
        merchant.barrier = bothRunning;
        UUID id = accept();

        orchestrator.process(id);

        assertThat(intent(id).status())
                .as("se as duas etapas rodassem em sequência, a barreira estouraria o tempo e o pagamento não confirmaria")
                .isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    void ignoresPaymentsThatAlreadyLeftAccepted() {
        UUID id = accept();
        orchestrator.process(id);
        reserve.calls.clear();

        orchestrator.process(id);

        assertThat(reserve.calls).isEmpty();
    }

    private UUID accept() {
        UUID id = UUID.randomUUID();
        payments.accept(new NewPayment(id, "12345678901", "0001", "000123", CNPJ, 100));
        return id;
    }

    private PaymentIntent intent(UUID id) {
        return payments.find(id).orElseThrow();
    }

    private static void await(CyclicBarrier barrier) {
        if (barrier == null) {
            return;
        }
        try {
            barrier.await(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("etapas não rodaram em paralelo", e);
        }
    }

    private static final class FakeReserve implements ReserveGateway {

        final List<String> calls = new ArrayList<>();
        Optional<FailureReason> failure = Optional.empty();
        boolean explode;
        CyclicBarrier barrier;

        @Override
        public synchronized Optional<FailureReason> reserve(PaymentIntent intent) {
            calls.add("reserve");
            if (explode) {
                throw new IllegalStateException("reserve fora do ar");
            }
            await(barrier);
            return failure;
        }

        @Override
        public synchronized void confirm(UUID paymentId) {
            calls.add("confirm");
        }

        @Override
        public synchronized void release(UUID paymentId) {
            calls.add("release");
        }
    }

    private static final class FakeMerchant implements MerchantGateway {

        final List<UUID> credits = new ArrayList<>();
        Optional<FailureReason> failure = Optional.empty();
        boolean explodeOnCredit;
        CyclicBarrier barrier;

        @Override
        public Optional<FailureReason> validate(String merchantCnpj) {
            await(barrier);
            return failure;
        }

        @Override
        public synchronized void credit(UUID paymentId, String merchantCnpj, long amount) {
            if (explodeOnCredit) {
                throw new IllegalStateException("merchant fora do ar");
            }
            credits.add(paymentId);
        }
    }
}
