package co.inter.piggies.coordinator.domain;

/**
 * Porta para avisar que o cliente foi debitado. O crédito do merchant acontece a partir desse aviso.
 * Falhas técnicas são exceções; o pagamento fica em {@link Stage#DEBITED} para ser republicado depois.
 */
public interface PaymentEventPublisher {

    void debited(PaymentIntent debited);
}
