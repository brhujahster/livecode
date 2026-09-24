package co.inter.piggies.merchant;

import co.inter.piggies.merchant.facade.CreditCommand;
import co.inter.piggies.merchant.facade.MerchantFacade;
import co.inter.piggies.merchant.facade.MerchantNotFoundException;
import co.inter.piggies.merchant.facade.MerchantStatus;
import co.inter.piggies.merchant.facade.ReceivableView;
import co.inter.piggies.support.AbstractContainersTest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest(transactional = false)
class MerchantServiceIT extends AbstractContainersTest {

    private static final String MERCHANT_X = "12345678000199";
    private static final String MERCHANT_Y = "98765432000155";

    @Inject
    MerchantFacade merchants;

    @Test
    void findsActiveMerchant() {
        assertThat(merchants.findByCnpj(MERCHANT_X)).hasValueSatisfying(merchant -> {
            assertThat(merchant.status()).isEqualTo(MerchantStatus.ACTIVE);
            assertThat(merchant.active()).isTrue();
            assertThat(merchant.name()).isEqualTo("Merchant X");
        });
    }

    @Test
    void findsInactiveMerchant() {
        assertThat(merchants.findByCnpj(MERCHANT_Y)).hasValueSatisfying(merchant ->
                assertThat(merchant.active()).isFalse());
    }

    @Test
    void unknownCnpjIsEmpty() {
        assertThat(merchants.findByCnpj("00000000000000")).isEmpty();
    }

    @Test
    void creditRecordsReceivableAndAddsToMerchantBalance() {
        long before = balanceOf(MERCHANT_X);
        UUID paymentId = UUID.randomUUID();

        ReceivableView receivable = merchants.credit(new CreditCommand(paymentId, MERCHANT_X, 100));

        assertThat(receivable.paymentId()).isEqualTo(paymentId);
        assertThat(receivable.merchantCnpj()).isEqualTo(MERCHANT_X);
        assertThat(receivable.amount()).isEqualTo(100);
        assertThat(receivable.creditedAt()).isNotNull();
        assertThat(merchants.findReceivable(paymentId)).isPresent();
        assertThat(balanceOf(MERCHANT_X)).isEqualTo(before + 100);
    }

    @Test
    void creditToUnknownMerchantFails() {
        UUID paymentId = UUID.randomUUID();

        assertThatThrownBy(() -> merchants.credit(new CreditCommand(paymentId, "00000000000000", 100)))
                .isInstanceOf(MerchantNotFoundException.class);
        assertThat(merchants.findReceivable(paymentId)).isEmpty();
    }

    private long balanceOf(String cnpj) {
        return merchants.findByCnpj(cnpj).orElseThrow().balance();
    }
}
