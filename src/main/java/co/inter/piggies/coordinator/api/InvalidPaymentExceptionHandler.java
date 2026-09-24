package co.inter.piggies.coordinator.api;

import co.inter.piggies.coordinator.domain.InvalidPaymentException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.ThrowableProblem;

@Singleton
public class InvalidPaymentExceptionHandler implements ExceptionHandler<InvalidPaymentException, HttpResponse<ThrowableProblem>> {

    @Override
    public HttpResponse<ThrowableProblem> handle(HttpRequest request, InvalidPaymentException exception) {
        ThrowableProblem problem = Problem.builder()
                .withTitle("Requisição inválida")
                .withStatus(Status.BAD_REQUEST)
                .withDetail(exception.getMessage())
                .build();
        return HttpResponse.badRequest(problem);
    }
}
