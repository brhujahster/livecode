package co.inter.piggies.coordinator.infra.persistence;

import co.inter.piggies.coordinator.domain.PaymentIntent;
import co.inter.piggies.coordinator.domain.PaymentIntentStore;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.UUID;

@Singleton
class JpaPaymentIntentStore implements PaymentIntentStore {

    private final PaymentIntentRepository repository;

    JpaPaymentIntentStore(PaymentIntentRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PaymentIntent> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public PaymentIntent insert(PaymentIntent intent) {
        return repository.save(intent);
    }

    @Override
    public PaymentIntent update(PaymentIntent intent) {
        return repository.update(intent);
    }
}
