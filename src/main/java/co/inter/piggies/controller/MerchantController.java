package co.inter.piggies.controller;

import co.inter.piggies.dto.CreateCreditRequest;
import co.inter.piggies.dto.ValidateMerchantRequest;
import co.inter.piggies.model.Merchant;
import co.inter.piggies.model.Receivable;
import co.inter.piggies.service.PiggiesMerchantService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class MerchantController {

    private final PiggiesMerchantService merchantService;

    @Post("/v1/merchants/validate")
    public HttpResponse<Merchant> validateMerchant(@Body ValidateMerchantRequest request) {
        Merchant merchant = merchantService.validateMerchant(request);
        return HttpResponse.ok(merchant);
    }

    @Post("/v1/credits")
    public HttpResponse<Receivable> createCredit(@Body CreateCreditRequest request) {
        Receivable receivable = merchantService.createCredit(request);
        return HttpResponse.created(receivable);
    }
}
