package co.inter.piggies.coordinator.web;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Corpo de erro do contrato: RFC 9457 com a extensão {@code code} para o motivo de negócio.
 */
@Serdeable
public record ApiProblem(String type, String title, int status, @Nullable String detail, String code) {

    static ApiProblem of(int status, String title, @Nullable String detail, String code) {
        return new ApiProblem("about:blank", title, status, detail, code);
    }
}
