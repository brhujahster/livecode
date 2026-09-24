package co.inter.piggies.merchant.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;


 //request para validar um merchant.

 //Contém apenas o CNPJ, que deve ter 14 dígitos.

@Serdeable
@Data
@Builder
public class ValidateMerchantRequest {

    @NotBlank(message = "merchantCnpj é obrigatório")
    @Pattern(
        regexp = "^[0-9]{14}$",
        message = "merchantCnpj deve ter 14 dígitos"
    )
    private String merchantCnpj;
}

