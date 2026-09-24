package co.inter.piggies.merchant.infra;

import co.inter.piggies.merchant.facade.MerchantStatus;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
@Requires(property = "spp.seed.enabled", notEquals = "false")
public class MerchantSeed {

    private final MerchantRepository merchants;
    private final MerchantAccountRepository accounts;

    public MerchantSeed(MerchantRepository merchants, MerchantAccountRepository accounts) {
        this.merchants = merchants;
        this.accounts = accounts;
    }

    @EventListener
    @Transactional
    public void onStartup(StartupEvent event) {
        seed();
    }

    @Transactional
    public void seed() {
        seedMerchant("Merchant X", "12345678000199", MerchantStatus.ACTIVE, "0001", "900001");
        seedMerchant("Merchant Y", "98765432000155", MerchantStatus.INACTIVE, "0001", "900002");
    }

    private void seedMerchant(String name, String cnpj, MerchantStatus status, String agency, String number) {
        if (merchants.findByCnpj(cnpj).isPresent()) {
            return;
        }
        Merchant merchant = merchants.save(new Merchant(name, cnpj, status));
        accounts.save(new MerchantAccount(merchant, agency, number, 0));
    }
}
