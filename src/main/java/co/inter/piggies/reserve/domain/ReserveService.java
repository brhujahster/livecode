package co.inter.piggies.reserve.domain;

import co.inter.piggies.reserve.facade.AccountView;
import co.inter.piggies.reserve.facade.ReservationConflictException;
import co.inter.piggies.reserve.facade.ReservationNotFoundException;
import co.inter.piggies.reserve.facade.ReservationStatus;
import co.inter.piggies.reserve.facade.ReservationView;
import co.inter.piggies.reserve.facade.ReserveCommand;
import co.inter.piggies.reserve.facade.ReserveFacade;
import co.inter.piggies.reserve.facade.ReserveResult;
import co.inter.piggies.reserve.facade.ReserveResult.RejectionReason;
import co.inter.piggies.reserve.infra.Account;
import co.inter.piggies.reserve.infra.AccountRepository;
import co.inter.piggies.reserve.infra.Reservation;
import co.inter.piggies.reserve.infra.ReservationRepository;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

import static co.inter.piggies.reserve.facade.ReservationConflictException.Code.IDEMPOTENCY_CONFLICT;
import static co.inter.piggies.reserve.facade.ReservationConflictException.Code.INVALID_RESERVATION_TRANSITION;

@Singleton
public class ReserveService implements ReserveFacade {

    private final AccountRepository accounts;
    private final ReservationRepository reservations;
    private final EntityManager entityManager;

    public ReserveService(AccountRepository accounts, ReservationRepository reservations, EntityManager entityManager) {
        this.accounts = accounts;
        this.reservations = reservations;
        this.entityManager = entityManager;
    }

    /**
     * A conta é travada antes de procurar a reserva: dois pedidos com o mesmo {@code paymentId} ficam em fila e o
     * segundo encontra a reserva gravada pelo primeiro, em vez de reservar de novo e falhar na chave primária.
     */
    @Override
    @Transactional
    public ReserveResult reserve(ReserveCommand command) {
        Optional<Account> locked = lockAccount(command.agency(), command.accountNumber());

        Optional<Reservation> existing = reservations.findById(command.paymentId());
        if (existing.isPresent()) {
            return new ReserveResult.Reserved(sameRequestOrConflict(existing.get(), command));
        }

        Optional<Account> account = locked.filter(found -> found.getClient().getCpf().equals(command.cpf()));
        if (account.isEmpty()) {
            return new ReserveResult.Rejected(RejectionReason.PAYER_ACCOUNT_NOT_FOUND);
        }

        if (accounts.reserve(account.get().getId(), command.amount()) == 0) {
            return new ReserveResult.Rejected(RejectionReason.INSUFFICIENT_BALANCE);
        }
        Reservation reservation = reservations.save(new Reservation(command.paymentId(), account.get(), command.amount()));
        return new ReserveResult.Reserved(view(reservation));
    }

    @Override
    @Transactional
    public ReservationView confirm(UUID paymentId) {
        Reservation reservation = lock(paymentId);
        return switch (reservation.getStatus()) {
            case CONFIRMED -> view(reservation);
            case RELEASED -> throw transitionConflict(reservation, ReservationStatus.CONFIRMED);
            case RESERVED -> {
                accounts.debitReserved(reservation.getAccount().getId(), reservation.getAmount());
                reservation.confirm();
                yield view(reservation);
            }
        };
    }

    @Override
    @Transactional
    public ReservationView release(UUID paymentId) {
        Reservation reservation = lock(paymentId);
        return switch (reservation.getStatus()) {
            case RELEASED -> view(reservation);
            case CONFIRMED -> throw transitionConflict(reservation, ReservationStatus.RELEASED);
            case RESERVED -> {
                accounts.releaseReserved(reservation.getAccount().getId(), reservation.getAmount());
                reservation.release();
                yield view(reservation);
            }
        };
    }

    @Override
    @Transactional
    public Optional<ReservationView> findReservation(UUID paymentId) {
        return reservations.findById(paymentId).map(this::view);
    }

    @Override
    @Transactional
    public Optional<AccountView> findAccount(String agency, String accountNumber) {
        return accounts.findByAgencyAndNumber(agency, accountNumber)
                .map(account -> new AccountView(
                        account.getAgency(),
                        account.getNumber(),
                        account.getClient().getCpf(),
                        account.getBalance(),
                        account.getReservedBalance(),
                        account.availableBalance()));
    }

    private Optional<Account> lockAccount(String agency, String number) {
        return entityManager.createQuery(
                        "SELECT a FROM Account a WHERE a.agency = :agency AND a.number = :number", Account.class)
                .setParameter("agency", agency)
                .setParameter("number", number)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst();
    }

    private Reservation lock(UUID paymentId) {
        Reservation reservation = entityManager.find(Reservation.class, paymentId, LockModeType.PESSIMISTIC_WRITE);
        if (reservation == null) {
            throw new ReservationNotFoundException(paymentId);
        }
        return reservation;
    }

    private ReservationView sameRequestOrConflict(Reservation reservation, ReserveCommand command) {
        Account account = reservation.getAccount();
        boolean sameRequest = reservation.getAmount() == command.amount()
                && account.getAgency().equals(command.agency())
                && account.getNumber().equals(command.accountNumber())
                && account.getClient().getCpf().equals(command.cpf());
        if (!sameRequest) {
            throw new ReservationConflictException(IDEMPOTENCY_CONFLICT,
                    "Já existe reserva para o pagamento " + command.paymentId() + " com outros dados");
        }
        return view(reservation);
    }

    private ReservationConflictException transitionConflict(Reservation reservation, ReservationStatus target) {
        return new ReservationConflictException(INVALID_RESERVATION_TRANSITION,
                "Reserva " + reservation.getPaymentId() + " está " + reservation.getStatus() + " e não pode ir para " + target);
    }

    private ReservationView view(Reservation reservation) {
        Account account = reservation.getAccount();
        return new ReservationView(
                reservation.getPaymentId(),
                account.getAgency(),
                account.getNumber(),
                reservation.getAmount(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
