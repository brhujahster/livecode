package co.inter.piggies.coordinator.client;

import co.inter.piggies.coordinator.client.merchant.MerchantsApi;
import co.inter.piggies.coordinator.client.merchant.model.Merchant;
import co.inter.piggies.coordinator.client.merchant.model.MerchantStatus;
import co.inter.piggies.coordinator.client.merchant.model.ValidateMerchantRequest;
import co.inter.piggies.coordinator.domain.FailureReasons;
import co.inter.piggies.coordinator.orchestration.MerchantGateway;
import co.inter.piggies.coordinator.orchestration.MerchantOutcome;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;

@Singleton
public class MerchantGatewayAdapter implements MerchantGateway {

    private final MerchantsApi merchants;

    public MerchantGatewayAdapter(MerchantsApi merchants) {
        this.merchants = merchants;
    }

    @Override
    public MerchantOutcome validate(String merchantCnpj) {
        try {
            Merchant merchant = merchants.validateMerchant(new ValidateMerchantRequest(merchantCnpj));
            if (merchant == null || merchant.getStatus() != MerchantStatus.ACTIVE) {
                return MerchantOutcome.rejected(FailureReasons.MERCHANT_INACTIVE);
            }
            return MerchantOutcome.accepted();
        } catch (HttpClientResponseException exception) {
            if (DownstreamErrors.isRejection(exception.getStatus())) {
                return MerchantOutcome.rejected(DownstreamErrors.detail(exception, DownstreamErrors.merchantFallback(exception.getStatus())));
            }
            throw exception;
        }
    }
}
