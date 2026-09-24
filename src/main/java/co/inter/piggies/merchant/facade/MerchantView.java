package co.inter.piggies.merchant.facade;

public record MerchantView(String cnpj, String name, MerchantStatus status,
                           String agency, String accountNumber, long balance) {

    public boolean active() {
        return status == MerchantStatus.ACTIVE;
    }
}
