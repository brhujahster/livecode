package co.inter.piggies.reserve.web;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Corpo de erro de {@code reserve.openapi.yaml}: RFC 9457 com a extensão {@code code}. Cópia própria do módulo,
 * sem classe compartilhada com o coordinator.
 */
@Serdeable
public record ReserveProblem(String type, String title, int status, @Nullable String detail, String code) {

    static MutableHttpResponse<ReserveProblem> response(HttpStatus status, String title, String detail, String code) {
        return HttpResponse.<ReserveProblem>status(status)
                .contentType(MediaType.APPLICATION_JSON_PROBLEM_TYPE)
                .body(new ReserveProblem("about:blank", title, status.getCode(), detail, code));
    }
}
