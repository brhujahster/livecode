package co.inter.piggies.dto;

import io.micronaut.serde.annotation.Serdeable;
import lombok.*;

import java.util.UUID;

@Serdeable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCreditRequest {
    private UUID paymentIntentId;
    private String merchantCnpj;
    private Long amount;
}
