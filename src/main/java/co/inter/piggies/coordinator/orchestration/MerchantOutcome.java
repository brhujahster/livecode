package co.inter.piggies.coordinator.orchestration;

public record MerchantOutcome(boolean succeeded, String failureReason) {

    public static MerchantOutcome accepted() {
        return new MerchantOutcome(true, null);
    }

    public static MerchantOutcome rejected(String failureReason) {
        return new MerchantOutcome(false, failureReason);
    }
}
