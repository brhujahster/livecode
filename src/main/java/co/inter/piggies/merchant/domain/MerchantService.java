package co.inter.piggies.merchant.domain;

import co.inter.piggies.merchant.facade.CreditCommand;
import co.inter.piggies.merchant.facade.MerchantFacade;
import co.inter.piggies.merchant.facade.MerchantNotFoundException;
import co.inter.piggies.merchant.facade.MerchantView;
import co.inter.piggies.merchant.facade.ReceivableView;
import co.inter.piggies.merchant.infra.Merchant;
import co.inter.piggies.merchant.infra.MerchantAccount;
import co.inter.piggies.merchant.infra.MerchantAccountRepository;
import co.inter.piggies.merchant.infra.MerchantRepository;
import co.inter.piggies.merchant.infra.Receivable;
import co.inter.piggies.merchant.infra.ReceivableRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class MerchantService implements MerchantFacade {

    private final MerchantRepository merchants;
    private final MerchantAccountRepository accounts;
    private final ReceivableRepository receivables;

    public MerchantService(MerchantRepository merchants, MerchantAccountRepository accounts, ReceivableRepository receivables) {
        this.merchants = merchants;
        this.accounts = accounts;
        this.receivables = receivables;
    }

    @Override
    @Transactional
    public Optional<MerchantView> findByCnpj(String cnpj) {
        return merchants.findByCnpj(cnpj).map(merchant -> {
            MerchantAccount account = accounts.findByMerchantId(merchant.getId()).orElseThrow();
            return new MerchantView(merchant.getCnpj(), merchant.getName(), merchant.getStatus(),
                    account.getAgency(), account.getNumber(), account.getBalance());
        });
    }

    @Override
    @Transactional
    public ReceivableView credit(CreditCommand command) {
        Optional<Receivable> existing = receivables.findById(command.paymentId());
        if (existing.isPresent()) {
            return view(existing.get());
        }
        Merchant merchant = merchants.findByCnpj(command.merchantCnpj())
                .orElseThrow(() -> new MerchantNotFoundException(command.merchantCnpj()));
        Receivable receivable = receivables.save(new Receivable(command.paymentId(), merchant, command.amount()));
        accounts.credit(merchant.getId(), command.amount());
        return view(receivable);
    }

    @Override
    @Transactional
    public Optional<ReceivableView> findReceivable(UUID paymentId) {
        return receivables.findById(paymentId).map(this::view);
    }

    private ReceivableView view(Receivable receivable) {
        return new ReceivableView(receivable.getPaymentId(), receivable.getMerchant().getCnpj(),
                receivable.getAmount(), receivable.getCreditedAt());
    }
}
