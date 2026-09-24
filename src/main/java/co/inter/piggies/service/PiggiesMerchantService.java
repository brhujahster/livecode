package co.inter.piggies.service;

import co.inter.piggies.dto.CreateCreditRequest;
import co.inter.piggies.dto.ValidateMerchantRequest;
import co.inter.piggies.model.Merchant;
import co.inter.piggies.model.Receivable;

public interface PiggiesMerchantService {
    Merchant validateMerchant(ValidateMerchantRequest request);
    Receivable createCredit(CreateCreditRequest request);
}
