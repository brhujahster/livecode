package co.inter.piggies.coordinator.support;

import co.inter.piggies.coordinator.domain.PaymentIntent;
import co.inter.piggies.coordinator.domain.PaymentIntentStore;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPaymentIntentStore implements PaymentIntentStore {

    private final Map<UUID, PaymentIntent> intents = new ConcurrentHashMap<>();

    @Override
    public Optional<PaymentIntent> findById(UUID id) {
        return Optional.ofNullable(intents.get(id));
    }

    @Override
    public PaymentIntent insert(PaymentIntent intent) {
        intents.put(intent.getId(), intent);
        return intent;
    }

    @Override
    public PaymentIntent update(PaymentIntent intent) {
        intents.put(intent.getId(), intent);
        return intent;
    }
}
