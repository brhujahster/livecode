package co.inter.piggies.reserve.web;

import co.inter.piggies.reserve.facade.ReserveFacade;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import jakarta.validation.constraints.Pattern;

@Controller("/v1/accounts")
class AccountsController {

    private final ReserveFacade reserve;

    AccountsController(ReserveFacade reserve) {
        this.reserve = reserve;
    }

    @Get("/{agency}/{accountNumber}")
    HttpResponse<?> get(@Pattern(regexp = "^[0-9]{4}$") String agency,
                        @Pattern(regexp = "^[0-9]{1,20}$") String accountNumber) {
        return reserve.findAccount(agency, accountNumber)
                .<HttpResponse<?>>map(account -> HttpResponse.ok(AccountResponse.from(account)))
                .orElseGet(() -> ReserveProblem.response(HttpStatus.NOT_FOUND, "Conta não encontrada",
                        "Nenhuma conta " + accountNumber + " na agência " + agency, "ACCOUNT_NOT_FOUND"));
    }
}
