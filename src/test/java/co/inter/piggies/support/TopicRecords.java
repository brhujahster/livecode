package co.inter.piggies.support;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Lê tudo o que já está publicado num tópico, sem grupo de consumo, para conferir eventos nos testes.
 */
public final class TopicRecords {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private TopicRecords() {
    }

    public static List<ConsumerRecord<String, String>> readAll(String bootstrapServers, String topic) {
        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        try (var consumer = new KafkaConsumer<String, String>(config)) {
            List<TopicPartition> partitions = consumer.partitionsFor(topic, TIMEOUT).stream()
                    .map(info -> new TopicPartition(topic, info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions, TIMEOUT);

            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            Instant deadline = Instant.now().plus(TIMEOUT);
            while (partitions.stream().anyMatch(partition -> consumer.position(partition) < end.get(partition))) {
                if (Instant.now().isAfter(deadline)) {
                    throw new IllegalStateException("Não consegui ler o tópico " + topic + " até o fim");
                }
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records;
        }
    }
}
