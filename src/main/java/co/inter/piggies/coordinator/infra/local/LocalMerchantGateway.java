package co.inter.piggies.coordinator.infra.local;

import co.inter.piggies.coordinator.domain.FailureReason;
import co.inter.piggies.coordinator.domain.MerchantGateway;
import co.inter.piggies.merchant.facade.MerchantFacade;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Chama o módulo merchant no mesmo processo. Na extração, vira um cliente HTTP de merchant.openapi.yaml.
 */
@Singleton
class LocalMerchantGateway implements MerchantGateway {

    private final MerchantFacade merchants;

    LocalMerchantGateway(MerchantFacade merchants) {
        this.merchants = merchants;
    }

    @Override
    public Optional<FailureReason> validate(String merchantCnpj) {
        return merchants.findByCnpj(merchantCnpj)
                .map(merchant -> merchant.active() ? Optional.<FailureReason>empty() : Optional.of(FailureReason.MERCHANT_INACTIVE))
                .orElse(Optional.of(FailureReason.MERCHANT_NOT_FOUND));
    }
}
