package co.inter.piggies.service.impl;

import co.inter.piggies.dto.CreateCreditRequest;
import co.inter.piggies.dto.ValidateMerchantRequest;
import co.inter.piggies.model.Merchant;
import co.inter.piggies.model.MerchantStatus;
import co.inter.piggies.model.Receivable;
import co.inter.piggies.model.ReceivableStatus;
import co.inter.piggies.repository.MerchantRepository;
import co.inter.piggies.repository.ReceivableRepository;
import co.inter.piggies.service.PiggiesMerchantService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Singleton
@RequiredArgsConstructor
public class PiggiesMerchantServiceImpl implements PiggiesMerchantService {

    private final MerchantRepository merchantRepository;
    private final ReceivableRepository receivableRepository;

    @Override
    public Merchant validateMerchant(ValidateMerchantRequest request) {
        if (request.getMerchantCnpj() == null || request.getMerchantCnpj().length() != 14) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "merchantCnpj deve ter 14 dígitos");
        }

        Merchant merchant = merchantRepository.findByCnpj(request.getMerchantCnpj())
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Não há merchant para o CNPJ informado"));

        if (merchant.getStatus() == MerchantStatus.INACTIVE) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "O merchant não está ativo para receber Piggies");
        }

        return merchant;
    }

    @Override
    @Transactional
    public Receivable createCredit(CreateCreditRequest request) {
        if (request.getMerchantCnpj() == null || request.getMerchantCnpj().length() != 14) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "merchantCnpj deve ter 14 dígitos");
        }
        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "amount deve ser um inteiro maior que zero");
        }

        Optional<Receivable> existing = receivableRepository.findByPaymentIntentId(request.getPaymentIntentId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Merchant merchant = merchantRepository.findByCnpj(request.getMerchantCnpj())
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Não há merchant para o CNPJ informado"));

        if (merchant.getStatus() == MerchantStatus.INACTIVE) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "O merchant não está ativo para receber Piggies");
        }

        Receivable receivable = Receivable.builder()
                .paymentIntentId(request.getPaymentIntentId())
                .merchantId(merchant.getId())
                .amount(request.getAmount())
                .status(ReceivableStatus.CREDITED)
                .build();

        return receivableRepository.save(receivable);
    }
}
