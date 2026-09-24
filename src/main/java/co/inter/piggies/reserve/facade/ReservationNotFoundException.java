package co.inter.piggies.reserve.facade;

import java.util.UUID;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(UUID paymentId) {
        super("Nenhuma reserva para o pagamento " + paymentId);
    }
}
