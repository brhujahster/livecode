package co.inter.piggies.merchant.facade;

import java.util.UUID;

public record CreditCommand(UUID paymentId, String merchantCnpj, long amount) {
}
