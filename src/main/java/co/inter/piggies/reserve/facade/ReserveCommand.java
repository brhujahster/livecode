package co.inter.piggies.reserve.facade;

import java.util.UUID;

public record ReserveCommand(UUID paymentId, String cpf, String agency, String accountNumber, long amount) {
}
