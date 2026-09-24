package co.inter.piggies.coordinator.orchestration;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class AsyncPaymentOrchestration implements PaymentOrchestration {

    private final PaymentOrchestrator orchestrator;
    private final ExecutorService executor;

    public AsyncPaymentOrchestration(PaymentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
        this.executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "payment-accept");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void start(UUID paymentId) {
        executor.execute(() -> orchestrator.orchestrate(paymentId));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
