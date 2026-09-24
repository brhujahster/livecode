package co.inter.piggies.reserve;

import co.inter.piggies.reserve.facade.AccountView;
import co.inter.piggies.reserve.facade.ReservationConflictException;
import co.inter.piggies.reserve.facade.ReservationNotFoundException;
import co.inter.piggies.reserve.facade.ReservationStatus;
import co.inter.piggies.reserve.facade.ReserveCommand;
import co.inter.piggies.reserve.facade.ReserveFacade;
import co.inter.piggies.reserve.facade.ReserveResult;
import co.inter.piggies.reserve.facade.ReserveResult.RejectionReason;
import co.inter.piggies.reserve.infra.Account;
import co.inter.piggies.reserve.infra.AccountRepository;
import co.inter.piggies.reserve.infra.Client;
import co.inter.piggies.reserve.infra.ClientRepository;
import co.inter.piggies.support.AbstractContainersTest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest(transactional = false)
class ReserveServiceIT extends AbstractContainersTest {

    @Inject
    ReserveFacade reserve;

    @Inject
    ClientRepository clients;

    @Inject
    AccountRepository accounts;

    @Test
    void reservesWhenAvailableCoversTheAmount() {
        TestAccount account = newAccount(500);

        ReserveResult result = reserve.reserve(account.command(UUID.randomUUID(), 100));

        assertThat(result).isInstanceOfSatisfying(ReserveResult.Reserved.class,
                reserved -> assertThat(reserved.reservation().status()).isEqualTo(ReservationStatus.RESERVED));
        assertThat(account.view()).satisfies(view -> {
            assertThat(view.balance()).isEqualTo(500);
            assertThat(view.reservedBalance()).isEqualTo(100);
            assertThat(view.availableBalance()).isEqualTo(400);
        });
    }

    @Test
    void rejectsWhenAvailableIsInsufficient() {
        TestAccount account = newAccount(50);

        ReserveResult result = reserve.reserve(account.command(UUID.randomUUID(), 100));

        assertThat(result).isEqualTo(new ReserveResult.Rejected(RejectionReason.INSUFFICIENT_BALANCE));
        assertThat(account.view().reservedBalance()).isZero();
        assertThat(account.view().balance()).isEqualTo(50);
    }

    @Test
    void countsExistingReservationsAgainstTheAvailable() {
        TestAccount account = newAccount(150);
        reserve.reserve(account.command(UUID.randomUUID(), 100));

        ReserveResult second = reserve.reserve(account.command(UUID.randomUUID(), 100));

        assertThat(second).isEqualTo(new ReserveResult.Rejected(RejectionReason.INSUFFICIENT_BALANCE));
        assertThat(account.view().reservedBalance()).isEqualTo(100);
    }

    @Test
    void rejectsUnknownAccount() {
        ReserveResult result = reserve.reserve(new ReserveCommand(UUID.randomUUID(), "11111111111", "0001", "999999", 100));

        assertThat(result).isEqualTo(new ReserveResult.Rejected(RejectionReason.PAYER_ACCOUNT_NOT_FOUND));
    }

    @Test
    void rejectsAccountThatDoesNotBelongToTheCpf() {
        TestAccount account = newAccount(500);

        ReserveResult result = reserve.reserve(
                new ReserveCommand(UUID.randomUUID(), "00000000000", account.agency(), account.number(), 100));

        assertThat(result).isEqualTo(new ReserveResult.Rejected(RejectionReason.PAYER_ACCOUNT_NOT_FOUND));
        assertThat(account.view().reservedBalance()).isZero();
    }

    @Test
    void confirmDebitsBalanceAndReserved() {
        TestAccount account = newAccount(500);
        UUID paymentId = UUID.randomUUID();
        reserve.reserve(account.command(paymentId, 100));

        assertThat(reserve.confirm(paymentId).status()).isEqualTo(ReservationStatus.CONFIRMED);

        assertThat(account.view()).satisfies(view -> {
            assertThat(view.balance()).isEqualTo(400);
            assertThat(view.reservedBalance()).isZero();
            assertThat(view.availableBalance()).isEqualTo(400);
        });
    }

    @Test
    void releaseRestoresAvailableWithoutTouchingBalance() {
        TestAccount account = newAccount(500);
        UUID paymentId = UUID.randomUUID();
        reserve.reserve(account.command(paymentId, 100));

        assertThat(reserve.release(paymentId).status()).isEqualTo(ReservationStatus.RELEASED);

        assertThat(account.view()).satisfies(view -> {
            assertThat(view.balance()).isEqualTo(500);
            assertThat(view.reservedBalance()).isZero();
            assertThat(view.availableBalance()).isEqualTo(500);
        });
    }

    @Test
    void cannotConfirmAReleasedReservation() {
        TestAccount account = newAccount(500);
        UUID paymentId = UUID.randomUUID();
        reserve.reserve(account.command(paymentId, 100));
        reserve.release(paymentId);

        assertThatThrownBy(() -> reserve.confirm(paymentId))
                .isInstanceOfSatisfying(ReservationConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo(ReservationConflictException.Code.INVALID_RESERVATION_TRANSITION));
        assertThat(account.view().balance()).isEqualTo(500);
    }

    @Test
    void cannotReleaseAConfirmedReservation() {
        TestAccount account = newAccount(500);
        UUID paymentId = UUID.randomUUID();
        reserve.reserve(account.command(paymentId, 100));
        reserve.confirm(paymentId);

        assertThatThrownBy(() -> reserve.release(paymentId))
                .isInstanceOf(ReservationConflictException.class);
        assertThat(account.view().balance()).isEqualTo(400);
        assertThat(account.view().reservedBalance()).isZero();
    }

    @Test
    void confirmOrReleaseWithoutReservationIsNotFound() {
        UUID paymentId = UUID.randomUUID();

        assertThatThrownBy(() -> reserve.confirm(paymentId)).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> reserve.release(paymentId)).isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void reservingTwiceWithTheSameDataReservesOnce() {
        TestAccount account = newAccount(500);
        UUID paymentId = UUID.randomUUID();
        reserve.reserve(account.command(paymentId, 100));

        ReserveResult repeated = reserve.reserve(account.command(paymentId, 100));

        assertThat(repeated).isInstanceOf(ReserveResult.Reserved.class);
        assertThat(account.view().reservedBalance()).isEqualTo(100);
    }

    @Test
    void reservingAgainWithOtherDataIsConflict() {
        TestAccount account = newAccount(500);
        UUID paymentId = UUID.randomUUID();
        reserve.reserve(account.command(paymentId, 100));

        assertThatThrownBy(() -> reserve.reserve(account.command(paymentId, 200)))
                .isInstanceOfSatisfying(ReservationConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo(ReservationConflictException.Code.IDEMPOTENCY_CONFLICT));
        assertThat(account.view().reservedBalance()).isEqualTo(100);
    }

    @Test
    void confirmingTwiceDebitsOnce() {
        TestAccount account = newAccount(500);
        UUID paymentId = UUID.randomUUID();
        reserve.reserve(account.command(paymentId, 100));
        reserve.confirm(paymentId);

        assertThat(reserve.confirm(paymentId).status()).isEqualTo(ReservationStatus.CONFIRMED);

        assertThat(account.view().balance()).isEqualTo(400);
        assertThat(account.view().reservedBalance()).isZero();
    }

    @Test
    void releasingTwiceRestoresOnce() {
        TestAccount account = newAccount(500);
        UUID paymentId = UUID.randomUUID();
        reserve.reserve(account.command(paymentId, 100));
        reserve.release(paymentId);

        assertThat(reserve.release(paymentId).status()).isEqualTo(ReservationStatus.RELEASED);

        assertThat(account.view().balance()).isEqualTo(500);
        assertThat(account.view().reservedBalance()).isZero();
    }

    @Test
    void concurrentReservationsNeverExceedTheBalance() {
        TestAccount account = newAccount(500);

        List<ReserveResult> results = concurrently(10, () -> reserve.reserve(account.command(UUID.randomUUID(), 100)));

        assertThat(results).filteredOn(ReserveResult.Reserved.class::isInstance).hasSize(5);
        assertThat(results).filteredOn(new ReserveResult.Rejected(RejectionReason.INSUFFICIENT_BALANCE)::equals).hasSize(5);
        assertThat(account.view()).satisfies(view -> {
            assertThat(view.balance()).isEqualTo(500);
            assertThat(view.reservedBalance()).isEqualTo(500);
            assertThat(view.availableBalance()).isZero();
        });
    }

    @Test
    void concurrentReservationsWithTheSamePaymentIdReserveOnce() {
        TestAccount account = newAccount(500);
        UUID paymentId = UUID.randomUUID();

        List<ReserveResult> results = concurrently(10, () -> reserve.reserve(account.command(paymentId, 100)));

        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(ReserveResult.Reserved.class));
        assertThat(account.view().reservedBalance()).isEqualTo(100);
    }

    /**
     * Dispara as chamadas juntas, liberadas por uma barreira, e devolve os resultados. Uma exceção em qualquer
     * chamada falha o teste.
     */
    private static <T> List<T> concurrently(int calls, Callable<T> call) {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = IntStream.range(0, calls)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return call.call();
                    }))
                    .toList();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } catch (Exception e) {
            throw new AssertionError("chamada concorrente falhou", e);
        }
    }

    private TestAccount newAccount(long balance) {
        String cpf = randomDigits(11);
        String number = randomDigits(8);
        Client client = clients.save(new Client("Cliente " + cpf, cpf));
        accounts.save(new Account(client, "0001", number, balance));
        return new TestAccount(cpf, "0001", number);
    }

    private static String randomDigits(int length) {
        StringBuilder digits = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            digits.append(ThreadLocalRandom.current().nextInt(10));
        }
        return digits.toString();
    }

    private final class TestAccount {

        private final String cpf;
        private final String agency;
        private final String number;

        TestAccount(String cpf, String agency, String number) {
            this.cpf = cpf;
            this.agency = agency;
            this.number = number;
        }

        String agency() {
            return agency;
        }

        String number() {
            return number;
        }

        ReserveCommand command(UUID paymentId, long amount) {
            return new ReserveCommand(paymentId, cpf, agency, number, amount);
        }

        AccountView view() {
            return reserve.findAccount(agency, number).orElseThrow();
        }
    }
}
