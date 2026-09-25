package co.inter.piggies.merchant.web;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Corpo de erro de {@code merchant.openapi.yaml}: RFC 9457 com a extensão {@code code}. Cópia própria do módulo,
 * sem classe compartilhada com o coordinator.
 */
@Serdeable
public record MerchantProblem(String type, String title, int status, @Nullable String detail, String code) {

    static MutableHttpResponse<MerchantProblem> notFound(String title, String detail, String code) {
        return HttpResponse.<MerchantProblem>status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON_PROBLEM_TYPE)
                .body(new MerchantProblem("about:blank", title, HttpStatus.NOT_FOUND.getCode(), detail, code));
    }
}
