package co.inter.piggies.controller;

import co.inter.piggies.dto.CreatePaymentRequest;
import co.inter.piggies.dto.CreateReservationRequest;
import co.inter.piggies.dto.PaymentAccepted;
import co.inter.piggies.dto.ValidateMerchantRequest;
import co.inter.piggies.event.PaymentConfirmEvent;
import co.inter.piggies.kafka.PaymentConfirmProducer;
import co.inter.piggies.model.PaymentIntent;
import co.inter.piggies.model.PaymentStatus;
import co.inter.piggies.model.Reservation;
import co.inter.piggies.repository.PaymentIntentRepository;
import co.inter.piggies.service.PiggiesMerchantService;
import co.inter.piggies.service.PiggiesReserveService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.inject.Named;

@Slf4j
@Controller("/v1/payments")
public class PiggiesPaymentCoordinator {

    private final PaymentIntentRepository paymentIntentRepository;
    private final PiggiesReserveService reserveService;
    private final PiggiesMerchantService merchantService;
    private final PaymentConfirmProducer paymentConfirmProducer;
    private final ExecutorService executorService;

    public PiggiesPaymentCoordinator(
            PaymentIntentRepository paymentIntentRepository,
            PiggiesReserveService reserveService,
            PiggiesMerchantService merchantService,
            PaymentConfirmProducer paymentConfirmProducer
    ) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.reserveService = reserveService;
        this.merchantService = merchantService;
        this.paymentConfirmProducer = paymentConfirmProducer;
        this.executorService = Executors.newCachedThreadPool();
    }

    @Post
    public HttpResponse<PaymentAccepted> createPayment(
            @Header("Idempotency-Key") String idempotencyKeyHeader,
            @Body CreatePaymentRequest request
    ) {
        if (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank()) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }

        UUID paymentId;
        try {
            paymentId = UUID.fromString(idempotencyKeyHeader);
        } catch (IllegalArgumentException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key inválido");
        }

        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "amount deve ser um inteiro maior que zero");
        }

        Optional<PaymentIntent> existing = paymentIntentRepository.findById(paymentId);
        if (existing.isPresent()) {
            PaymentIntent intent = existing.get();
            return HttpResponse.accepted().body(
                    PaymentAccepted.builder()
                            .paymentId(intent.getId())
                            .status(intent.getStatus())
                            .build()
            );
        }

        PaymentIntent intent = PaymentIntent.builder()
                .id(paymentId)
                .payerCpf(request.getPayerCpf())
                .payerAgency(request.getPayerAgency())
                .payerAccount(request.getPayerAccount())
                .merchantCnpj(request.getMerchantCnpj())
                .amount(request.getAmount())
                .status(PaymentStatus.PROCESSING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        paymentIntentRepository.save(intent);

        // Orquestração assíncrona
        executorService.submit(() -> orchestratePayment(paymentId, request));

        return HttpResponse.accepted().body(
                PaymentAccepted.builder()
                        .paymentId(paymentId)
                        .status(PaymentStatus.PROCESSING)
                        .build()
        );
    }

    @Get("/{id}")
    public HttpResponse<PaymentIntent> getPayment(@PathVariable UUID id) {
        PaymentIntent intent = paymentIntentRepository.findById(id)
                .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Não existe intenção para o id informado"));
        return HttpResponse.ok(intent);
    }

    public void orchestratePayment(UUID paymentId, CreatePaymentRequest request) {
        Reservation reservation = null;
        try {
            // 1. Tentar criar reserva
            try {
                reservation = reserveService.createReservation(CreateReservationRequest.builder()
                        .paymentIntentId(paymentId)
                        .payerCpf(request.getPayerCpf())
                        .payerAgency(request.getPayerAgency())
                        .payerAccount(request.getPayerAccount())
                        .amount(request.getAmount())
                        .build());

                // Salva reservationId na intenção
                PaymentIntent intent = paymentIntentRepository.findById(paymentId).orElse(null);
                if (intent != null) {
                    intent.setReservationId(reservation.getId());
                    paymentIntentRepository.update(intent);
                }
            } catch (Exception e) {
                log.error("Erro na reserva para paymentId {}: {}", paymentId, e.getMessage());
                markAsFailed(paymentId, "Erro na reserva: " + e.getMessage());
                return;
            }

            // 2. Validar merchant
            try {
                merchantService.validateMerchant(ValidateMerchantRequest.builder()
                        .merchantCnpj(request.getMerchantCnpj())
                        .build());
            } catch (Exception e) {
                log.error("Erro na validação do merchant para paymentId {}: {}", paymentId, e.getMessage());
                if (reservation != null) {
                    try {
                        reserveService.releaseReservation(reservation.getId());
                    } catch (Exception re) {
                        log.error("Erro ao liberar reserva {} após falha do merchant: {}", reservation.getId(), re.getMessage());
                    }
                }
                markAsFailed(paymentId, "Merchant inválido ou inativo");
                return;
            }

            // 3. Confirmar débito da reserva
            try {
                reserveService.confirmReservation(reservation.getId());
            } catch (Exception e) {
                log.error("Erro ao confirmar reserva para paymentId {}: {}", paymentId, e.getMessage());
                markAsFailed(paymentId, "Erro na confirmação do débito");
                return;
            }

            // 4. Publicar evento spp.payment.confirm
            PaymentConfirmEvent confirmEvent = PaymentConfirmEvent.builder()
                    .paymentId(paymentId)
                    .reservationId(reservation.getId())
                    .merchantCnpj(request.getMerchantCnpj())
                    .amount(request.getAmount())
                    .build();

            log.info("Disparando spp.payment.confirm para paymentId: {}", paymentId);
            paymentConfirmProducer.sendPaymentConfirm(paymentId, confirmEvent);

        } catch (Exception ex) {
            log.error("Erro inesperado durante a orquestração do paymentId {}: {}", paymentId, ex.getMessage(), ex);
            markAsFailed(paymentId, "Erro inesperado: " + ex.getMessage());
        }
    }

    private void markAsFailed(UUID paymentId, String reason) {
        PaymentIntent intent = paymentIntentRepository.findById(paymentId).orElse(null);
        if (intent != null) {
            intent.setStatus(PaymentStatus.FAILED);
            intent.setFailureReason(reason);
            paymentIntentRepository.update(intent);
        }
    }
}
