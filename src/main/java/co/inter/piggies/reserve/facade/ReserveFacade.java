package co.inter.piggies.reserve.facade;

import java.util.Optional;
import java.util.UUID;

/**
 * API pública do módulo reserve: o único tipo do qual outros módulos podem depender.
 * Toda operação é idempotente pelo {@code paymentId}.
 */
public interface ReserveFacade {

    /**
     * @throws ReservationConflictException quando já existe reserva para o pagamento com outros dados
     */
    ReserveResult reserve(ReserveCommand command);

    /**
     * @throws ReservationNotFoundException quando não há reserva para o pagamento
     * @throws ReservationConflictException quando a reserva já foi liberada
     */
    ReservationView confirm(UUID paymentId);

    /**
     * @throws ReservationNotFoundException quando não há reserva para o pagamento
     * @throws ReservationConflictException quando a reserva já foi confirmada
     */
    ReservationView release(UUID paymentId);

    Optional<ReservationView> findReservation(UUID paymentId);

    Optional<AccountView> findAccount(String agency, String accountNumber);
}
