package co.inter.piggies.coordinator.web;

import co.inter.piggies.coordinator.domain.IdempotencyConflictException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Produces
@Singleton
class IdempotencyConflictExceptionHandler
        implements ExceptionHandler<IdempotencyConflictException, HttpResponse<ApiProblem>> {

    @Override
    public HttpResponse<ApiProblem> handle(HttpRequest request, IdempotencyConflictException exception) {
        return HttpResponse.<ApiProblem>status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON_PROBLEM_TYPE)
                .body(ApiProblem.of(409, "Conflito de idempotência", exception.getMessage(), "IDEMPOTENCY_CONFLICT"));
    }
}
