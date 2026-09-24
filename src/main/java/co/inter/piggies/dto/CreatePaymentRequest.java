package co.inter.piggies.dto;

import io.micronaut.serde.annotation.Serdeable;
import lombok.*;

@Serdeable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentRequest {
    private String payerCpf;
    private String payerAgency;
    private String payerAccount;
    private String merchantCnpj;
    private Long amount;
}
