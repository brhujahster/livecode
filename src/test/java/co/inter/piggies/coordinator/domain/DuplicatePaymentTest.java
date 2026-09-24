package co.inter.piggies.coordinator.domain;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicatePaymentTest {

    @Test
    void matchesOnlyUniqueViolationInTheCauseChain() {
        assertThat(DuplicatePayment.matches(null)).isFalse();
        assertThat(DuplicatePayment.matches(new RuntimeException("db down"))).isFalse();
        assertThat(DuplicatePayment.matches(new SQLException("other", "23506"))).isFalse();
        assertThat(DuplicatePayment.matches(new SQLException("duplicate", "23505"))).isTrue();
        assertThat(DuplicatePayment.matches(new RuntimeException(new SQLException("duplicate", "23505")))).isTrue();
    }
}
