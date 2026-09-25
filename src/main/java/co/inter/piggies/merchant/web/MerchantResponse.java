package co.inter.piggies.merchant.web;

import co.inter.piggies.merchant.facade.MerchantStatus;
import co.inter.piggies.merchant.facade.MerchantView;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record MerchantResponse(String cnpj, String name, MerchantStatus status, Account account) {

    @Serdeable
    public record Account(String agency, String accountNumber, long balance) {
    }

    static MerchantResponse from(MerchantView view) {
        return new MerchantResponse(view.cnpj(), view.name(), view.status(),
                new Account(view.agency(), view.accountNumber(), view.balance()));
    }
}
