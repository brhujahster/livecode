package co.inter.piggies.coordinator.client;

import co.inter.piggies.coordinator.domain.FailureReasons;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public final class DownstreamErrors {

    private DownstreamErrors() {
    }

    public static boolean isRejection(HttpStatus status) {
        int code = status.getCode();
        return code == 400 || code == 404 || code == 409 || code == 422;
    }

    public static String detail(HttpClientResponseException exception, String fallback) {
        String raw = exception.getResponse().getBody(String.class).orElse(null);
        String parsed = parseDetail(raw);
        return parsed == null ? fallback : parsed;
    }

    static String parseDetail(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int key = raw.indexOf("\"detail\"");
        if (key < 0) {
            return null;
        }
        int colon = raw.indexOf(':', key);
        if (colon < 0) {
            return null;
        }
        int start = raw.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        int end = raw.indexOf('"', start + 1);
        if (end < 0) {
            return null;
        }
        String value = raw.substring(start + 1, end);
        return value.isBlank() ? null : value;
    }

    public static String reserveFallback(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return FailureReasons.ACCOUNT_NOT_FOUND;
        }
        if (status == HttpStatus.UNPROCESSABLE_ENTITY) {
            return FailureReasons.INSUFFICIENT_FUNDS;
        }
        return FailureReasons.ORCHESTRATION_FAILED;
    }

    public static String merchantFallback(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return FailureReasons.MERCHANT_NOT_FOUND;
        }
        if (status == HttpStatus.UNPROCESSABLE_ENTITY) {
            return FailureReasons.MERCHANT_INACTIVE;
        }
        return FailureReasons.ORCHESTRATION_FAILED;
    }
}
