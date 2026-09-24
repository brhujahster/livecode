package co.inter.piggies.coordinator.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta para o serviço de merchant. Falhas de negócio voltam como {@link FailureReason};
 * falhas técnicas são exceções.
 */
public interface MerchantGateway {

    /**
     * @return vazio quando o merchant existe e está ativo, ou o motivo da recusa
     */
    Optional<FailureReason> validate(String merchantCnpj);

    void credit(UUID paymentId, String merchantCnpj, long amount);
}
