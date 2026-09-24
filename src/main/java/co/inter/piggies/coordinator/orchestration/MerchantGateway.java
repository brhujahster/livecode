package co.inter.piggies.coordinator.orchestration;

public interface MerchantGateway {

    MerchantOutcome validate(String merchantCnpj);
}
