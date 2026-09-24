package co.inter.piggies.coordinator.domain;

import java.sql.SQLException;

public final class DuplicatePayment {

    private DuplicatePayment() {
    }

    public static boolean matches(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
