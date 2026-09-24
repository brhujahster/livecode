package co.inter.piggies.support;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;

/**
 * Acesso direto ao Kafka nos testes: publicar mensagens cruas, ler um tópico inteiro e esperar um grupo de
 * consumo processar tudo o que já foi publicado.
 */
public final class TestKafka {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private TestKafka() {
    }

    public static void publish(String bootstrapServers, String topic, String key, String value) {
        Properties config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (var producer = new KafkaProducer<String, String>(config)) {
            producer.send(new ProducerRecord<>(topic, key, value)).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Não consegui publicar em " + topic, e);
        }
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

    /**
     * Espera o grupo confirmar offset até o fim de cada partição do tópico, ou seja, processar tudo o que já estava
     * publicado quando o método foi chamado.
     */
    public static void awaitConsumed(String bootstrapServers, String groupId, String topic) {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            Map<TopicPartition, Long> end = endOffsets(admin, topic);
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(groupId)
                        .partitionsToOffsetAndMetadata().get();
                return end.entrySet().stream().allMatch(partition -> partition.getValue() == 0
                        || (committed.get(partition.getKey()) != null
                        && committed.get(partition.getKey()).offset() >= partition.getValue()));
            });
        }
    }

    private static Map<TopicPartition, Long> endOffsets(Admin admin, String topic) {
        try {
            var description = admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic);
            Map<TopicPartition, OffsetSpec> request = description.partitions().stream()
                    .map(info -> new TopicPartition(topic, info.partition()))
                    .collect(Collectors.toMap(Function.identity(), partition -> OffsetSpec.latest()));
            return admin.listOffsets(request).all().get().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().offset()));
        } catch (Exception e) {
            throw new IllegalStateException("Não consegui ler os offsets de " + topic, e);
        }
    }
}
