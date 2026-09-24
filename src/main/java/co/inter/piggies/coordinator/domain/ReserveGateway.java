package co.inter.piggies.coordinator.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta para o serviço de reserva. Falhas de negócio voltam como {@link FailureReason};
 * falhas técnicas são exceções e deixam o pagamento no estágio em que estava.
 */
public interface ReserveGateway {

    /**
     * @return vazio quando a reserva foi feita, ou o motivo da recusa
     */
    Optional<FailureReason> reserve(PaymentIntent intent);

    void confirm(UUID paymentId);

    void release(UUID paymentId);
}
