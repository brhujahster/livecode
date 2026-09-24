package co.inter.piggies.coordinator.domain;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Singleton
public class PaymentService {

    public record Accepted(PaymentIntent intent, boolean created) {
    }

    private final PaymentIntentStore store;

    public PaymentService(PaymentIntentStore store) {
        this.store = store;
    }

    @Transactional
    public Accepted accept(NewPayment payment) {
        return store.findById(payment.paymentId())
                .map(existing -> new Accepted(existing, false))
                .orElseGet(() -> new Accepted(store.insert(payment.toIntent()), true));
    }

    @Transactional
    public Optional<PaymentIntent> find(UUID paymentId) {
        return store.findById(paymentId);
    }

    @Transactional
    public void markDebited(UUID paymentId) {
        change(paymentId, PaymentIntent::markDebited);
    }

    @Transactional
    public void confirm(UUID paymentId) {
        change(paymentId, PaymentIntent::confirm);
    }

    @Transactional
    public void fail(UUID paymentId, FailureReason reason) {
        change(paymentId, intent -> intent.fail(reason));
    }

    private void change(UUID paymentId, Consumer<PaymentIntent> transition) {
        PaymentIntent intent = store.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Pagamento " + paymentId + " não existe"));
        transition.accept(intent);
        store.update(intent);
    }
}
