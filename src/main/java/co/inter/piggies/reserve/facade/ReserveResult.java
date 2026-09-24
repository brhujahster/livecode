package co.inter.piggies.reserve.facade;

public sealed interface ReserveResult {

    record Reserved(ReservationView reservation) implements ReserveResult {
    }

    record Rejected(RejectionReason reason) implements ReserveResult {
    }

    enum RejectionReason {
        PAYER_ACCOUNT_NOT_FOUND,
        INSUFFICIENT_BALANCE
    }
}
