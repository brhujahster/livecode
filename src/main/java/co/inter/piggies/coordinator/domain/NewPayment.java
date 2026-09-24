package co.inter.piggies.coordinator.domain;

import java.util.UUID;

public record NewPayment(UUID paymentId, String payerCpf, String payerAgency, String payerAccountNumber,
                         String merchantCnpj, long amount) {

    PaymentIntent toIntent() {
        return new PaymentIntent(paymentId, payerCpf, payerAgency, payerAccountNumber, merchantCnpj, amount);
    }

    boolean sameDataAs(PaymentIntent intent) {
        return intent.getPayerCpf().equals(payerCpf)
                && intent.getPayerAgency().equals(payerAgency)
                && intent.getPayerAccountNumber().equals(payerAccountNumber)
                && intent.getMerchantCnpj().equals(merchantCnpj)
                && intent.getAmount() == amount;
    }
}
