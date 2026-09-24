package co.inter.piggies.coordinator.domain;

import java.util.Optional;

/**
 * Porta para o serviço de merchant. Falhas de negócio voltam como {@link FailureReason};
 * falhas técnicas são exceções. O crédito não passa por aqui: vem do evento de débito.
 */
public interface MerchantGateway {

    /**
     * @return vazio quando o merchant existe e está ativo, ou o motivo da recusa
     */
    Optional<FailureReason> validate(String merchantCnpj);
}
