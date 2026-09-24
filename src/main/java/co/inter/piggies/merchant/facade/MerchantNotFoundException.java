package co.inter.piggies.merchant.facade;

public class MerchantNotFoundException extends RuntimeException {

    public MerchantNotFoundException(String cnpj) {
        super("Nenhum merchant com CNPJ " + cnpj);
    }
}
