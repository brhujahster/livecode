package co.inter.piggies.coordinator.domain;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Singleton
public class PaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentService.class);

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
    public PaymentIntent markDebited(UUID paymentId) {
        return change(paymentId, PaymentIntent::markDebited);
    }

    /**
     * Fecha o pagamento quando o merchant avisa que creditou. O aviso pode chegar repetido ou atrasado:
     * pagamento já confirmado ou desconhecido é ignorado, para o consumidor não ficar preso na mesma mensagem.
     */
    @Transactional
    public void confirmCredited(UUID paymentId) {
        Optional<PaymentIntent> found = store.findById(paymentId);
        if (found.isEmpty()) {
            LOG.warn("Confirmação para pagamento desconhecido {}; ignorada", paymentId);
            return;
        }
        PaymentIntent intent = found.get();
        switch (intent.getStage()) {
            case DEBITED -> {
                intent.confirm();
                store.update(intent);
            }
            case CONFIRMED -> LOG.debug("Confirmação repetida para {}; ignorada", paymentId);
            case ACCEPTED, FAILED ->
                    LOG.warn("Confirmação para pagamento {} em {}; ignorada", paymentId, intent.getStage());
        }
    }

    @Transactional
    public void fail(UUID paymentId, FailureReason reason) {
        change(paymentId, intent -> intent.fail(reason));
    }

    private PaymentIntent change(UUID paymentId, Consumer<PaymentIntent> transition) {
        PaymentIntent intent = store.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Pagamento " + paymentId + " não existe"));
        transition.accept(intent);
        return store.update(intent);
    }
}
