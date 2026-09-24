package co.inter.piggies.coordinator.domain;

public final class FailureReasons {

    public static final String INSUFFICIENT_FUNDS = "Saldo insuficiente";
    public static final String ACCOUNT_NOT_FOUND = "Conta não encontrada";
    public static final String MERCHANT_INACTIVE = "Merchant inativo";
    public static final String MERCHANT_NOT_FOUND = "Merchant não encontrado";
    public static final String PUBLISH_FAILED = "Falha ao publicar a confirmação do pagamento";
    public static final String RELEASE_FAILED_SUFFIX = "falha ao liberar a reserva";
    public static final String DIVERGENT_CREDIT = "Evento de crédito divergente da intenção";
    public static final String ORCHESTRATION_FAILED = "Falha na orquestração do pagamento";
    public static final String AMOUNT_INVALID = "amount deve ser um inteiro maior que zero";
    public static final String PAYMENT_ID_REQUIRED = "Idempotency-Key é obrigatória";

    private FailureReasons() {
    }
}
