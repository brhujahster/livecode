package co.inter.piggies.coordinator.domain;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentStore {

    Optional<PaymentIntent> findById(UUID id);

    /**
     * Grava a intenção se ainda não existir outra com o mesmo id. Se houver uma gravação concorrente em andamento,
     * espera ela terminar em vez de falhar.
     *
     * @return {@code true} se esta chamada criou a intenção
     */
    boolean insertIfAbsent(PaymentIntent intent);

    PaymentIntent update(PaymentIntent intent);
}
