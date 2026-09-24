package co.inter.piggies.coordinator.web;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.math.BigInteger;

/**
 * Aceita só números inteiros do JSON. O desserializador padrão converte 10.5 em 10 e "100" em 100,
 * o que faria um valor fracionário virar pagamento.
 */
@Singleton
class WholeNumberDeserializer implements Deserializer<Long> {

    @Override
    public Long deserialize(Decoder decoder, DecoderContext context, Argument<? super Long> type) throws IOException {
        Object value = decoder.decodeArbitrary();
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger big && big.bitLength() < Long.SIZE) {
            return big.longValue();
        }
        throw new SerdeException("Esperava um número inteiro, recebeu " + value);
    }
}
