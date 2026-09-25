package co.inter.piggies.reserve.web;

import co.inter.piggies.reserve.facade.AccountView;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AccountResponse(String agency, String accountNumber, String ownerCpf, long balance,
                              long reservedBalance, long availableBalance) {

    static AccountResponse from(AccountView view) {
        return new AccountResponse(view.agency(), view.accountNumber(), view.ownerCpf(), view.balance(),
                view.reservedBalance(), view.availableBalance());
    }
}
