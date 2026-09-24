package co.inter.piggies.dto;

import io.micronaut.serde.annotation.Serdeable;
import lombok.*;

@Serdeable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidateMerchantRequest {
    private String merchantCnpj;
}
