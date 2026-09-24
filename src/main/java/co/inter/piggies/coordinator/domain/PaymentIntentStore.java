package co.inter.piggies.coordinator.domain;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentStore {

    Optional<PaymentIntent> findById(UUID id);

    PaymentIntent insert(PaymentIntent intent);

    PaymentIntent update(PaymentIntent intent);
}
