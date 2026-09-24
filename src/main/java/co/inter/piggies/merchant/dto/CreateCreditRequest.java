package co.inter.piggies.merchant.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;


// Request para registrar um crédito.

@Serdeable
@Data
@Builder
public class CreateCreditRequest {

    @NotNull(message = "paymentIntentId é obrigatório")
    private UUID paymentIntentId;

    @NotBlank(message = "merchantCnpj é obrigatório")
    @Pattern(
        regexp = "^[0-9]{14}$",
        message = "merchantCnpj deve ter 14 dígitos"
    )
    private String merchantCnpj;

    @NotNull(message = "amount é obrigatório")
    @Min(value = 1, message = "amount deve ser maior que 0")
    private Long amount;
}

