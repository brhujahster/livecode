package co.inter.piggies.merchant.web;

import co.inter.piggies.merchant.facade.MerchantFacade;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.UUID;

@Controller("/v1/receivables")
class ReceivablesController {

    private final MerchantFacade merchants;

    ReceivablesController(MerchantFacade merchants) {
        this.merchants = merchants;
    }

    @Get("/{paymentId}")
    HttpResponse<?> get(UUID paymentId) {
        return merchants.findReceivable(paymentId)
                .<HttpResponse<?>>map(receivable -> HttpResponse.ok(ReceivableResponse.from(receivable)))
                .orElseGet(() -> MerchantProblem.notFound("Recebível não encontrado",
                        "Nenhum recebível para o pagamento " + paymentId, "RECEIVABLE_NOT_FOUND"));
    }
}
