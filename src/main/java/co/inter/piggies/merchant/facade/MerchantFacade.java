package co.inter.piggies.merchant.facade;

import java.util.Optional;
import java.util.UUID;

/**
 * API pública do módulo merchant: o único tipo do qual outros módulos podem depender.
 */
public interface MerchantFacade {

    Optional<MerchantView> findByCnpj(String cnpj);

    /**
     * Grava o recebível e soma o valor ao saldo do merchant. Idempotente pelo {@code paymentId}.
     * Não checa se o merchant está ativo: o cliente já foi debitado e o dinheiro precisa chegar.
     *
     * @throws MerchantNotFoundException quando o CNPJ não pertence a nenhum merchant
     */
    ReceivableView credit(CreditCommand command);

    Optional<ReceivableView> findReceivable(UUID paymentId);
}
