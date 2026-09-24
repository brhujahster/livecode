package co.inter.piggies.coordinator.persistence;

import co.inter.piggies.coordinator.domain.PaymentIntentEntity;
import co.inter.piggies.coordinator.domain.PaymentIntentRepository;
import co.inter.piggies.coordinator.domain.PaymentIntentStore;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class JpaPaymentIntentStore implements PaymentIntentStore {

    private final PaymentIntentRepository repository;

    public JpaPaymentIntentStore(PaymentIntentRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PaymentIntentEntity> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public void insert(PaymentIntentEntity intent) {
        repository.save(intent);
    }

    @Override
    public void update(PaymentIntentEntity intent) {
        repository.update(intent);
    }
}
