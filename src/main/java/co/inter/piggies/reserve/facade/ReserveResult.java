package co.inter.piggies.reserve.facade;

public sealed interface ReserveResult {

    /**
     * @param created {@code false} quando a reserva já existia com os mesmos dados
     */
    record Reserved(ReservationView reservation, boolean created) implements ReserveResult {
    }

    record Rejected(RejectionReason reason) implements ReserveResult {
    }

    enum RejectionReason {
        PAYER_ACCOUNT_NOT_FOUND,
        INSUFFICIENT_BALANCE
    }
}
