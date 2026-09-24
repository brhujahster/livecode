package co.inter.piggies.coordinator.web;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Serdeable
public record QrCode(
        @NotNull @Pattern(regexp = "^[0-9]{14}$") String merchantCnpj,
        @Serdeable.Deserializable(using = WholeNumberDeserializer.class) @NotNull @Min(1) Long amount) {
}
