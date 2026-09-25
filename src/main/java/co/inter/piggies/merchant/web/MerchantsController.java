package co.inter.piggies.merchant.web;

import co.inter.piggies.merchant.facade.MerchantFacade;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import jakarta.validation.constraints.Pattern;

@Controller("/v1/merchants")
class MerchantsController {

    private final MerchantFacade merchants;

    MerchantsController(MerchantFacade merchants) {
        this.merchants = merchants;
    }

    @Get("/{cnpj}")
    HttpResponse<?> get(@Pattern(regexp = "^[0-9]{14}$") String cnpj) {
        return merchants.findByCnpj(cnpj)
                .<HttpResponse<?>>map(merchant -> HttpResponse.ok(MerchantResponse.from(merchant)))
                .orElseGet(() -> MerchantProblem.notFound("Merchant não encontrado",
                        "Nenhum merchant com o CNPJ " + cnpj, "MERCHANT_NOT_FOUND"));
    }
}
