package co.inter.piggies.coordinator.domain;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentStore {

    Optional<PaymentIntentEntity> findById(UUID id);

    void insert(PaymentIntentEntity intent);

    void update(PaymentIntentEntity intent);
}
