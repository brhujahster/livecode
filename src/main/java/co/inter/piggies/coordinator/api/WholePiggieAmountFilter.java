package co.inter.piggies.coordinator.api;

import co.inter.piggies.coordinator.domain.FailureReasons;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.ThrowableProblem;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@ServerFilter("/v1/payments")
public class WholePiggieAmountFilter {

    private static final Pattern FRACTIONAL_AMOUNT = Pattern.compile("\"amount\"\\s*:\\s*-?\\d+\\.\\d+");

    @RequestFilter
    @Nullable
    public HttpResponse<?> rejectFractionalAmount(HttpRequest<?> request, @Body @Nullable byte[] body) {
        if (request.getMethod() != HttpMethod.POST || body == null || body.length == 0) {
            return null;
        }
        String json = new String(body, StandardCharsets.UTF_8);
        if (!FRACTIONAL_AMOUNT.matcher(json).find()) {
            return null;
        }
        ThrowableProblem problem = Problem.builder()
                .withTitle("Requisição inválida")
                .withStatus(Status.BAD_REQUEST)
                .withDetail(FailureReasons.AMOUNT_INVALID)
                .build();
        return HttpResponse.badRequest(problem);
    }
}
