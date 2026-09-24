package co.inter.piggies.coordinator.web;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Produces
@Singleton
class ApiProblemExceptionHandler implements ExceptionHandler<ApiProblemException, HttpResponse<ApiProblem>> {

    @Override
    public HttpResponse<ApiProblem> handle(HttpRequest request, ApiProblemException exception) {
        ApiProblem problem = exception.problem();
        return HttpResponse.<ApiProblem>status(HttpStatus.valueOf(problem.status()))
                .contentType(MediaType.APPLICATION_JSON_PROBLEM_TYPE)
                .body(problem);
    }
}
