package co.inter.piggies.coordinator.web;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.exceptions.response.Error;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.problem.ProblemErrorResponseProcessor;
import jakarta.inject.Singleton;

import java.util.stream.Collectors;

/**
 * Erros gerados pelo framework (validação, JSON malformado, header ausente, rota inexistente) saem no mesmo
 * formato {@link ApiProblem} do contrato, com {@code title} e {@code code}.
 */
@Singleton
@Replaces(ProblemErrorResponseProcessor.class)
class ApiProblemErrorResponseProcessor implements ErrorResponseProcessor<ApiProblem> {

    static final String INVALID_REQUEST = "INVALID_REQUEST";

    @Override
    public MutableHttpResponse<ApiProblem> processResponse(ErrorContext context, MutableHttpResponse<?> response) {
        HttpStatus status = response.status();
        ApiProblem problem = status == HttpStatus.BAD_REQUEST
                ? ApiProblem.of(status.getCode(), "Requisição inválida", details(context), INVALID_REQUEST)
                : ApiProblem.of(status.getCode(), status.getReason(), clientDetails(status, context), status.name());
        return response.contentType(MediaType.APPLICATION_JSON_PROBLEM_TYPE).body(problem);
    }

    private static String clientDetails(HttpStatus status, ErrorContext context) {
        // Em 5xx a mensagem da exceção pode expor detalhes internos.
        return status.getCode() < 500 ? details(context) : null;
    }

    private static String details(ErrorContext context) {
        if (!context.hasErrors()) {
            return null;
        }
        return context.getErrors().stream()
                .map(ApiProblemErrorResponseProcessor::describe)
                .collect(Collectors.joining("; "));
    }

    private static String describe(Error error) {
        return error.getPath().map(path -> path + ": " + error.getMessage()).orElse(error.getMessage());
    }
}
