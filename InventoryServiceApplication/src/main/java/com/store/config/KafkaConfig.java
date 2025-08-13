package com.store.config;

import com.store.dto.OrderCreated;
import com.store.dto.ReleaseStock;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    /* ------------------- COMMON CONSUMER ------------------- */
    private Map<String, Object> baseConsumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        return props;
    }

    @Bean(name = "orderCreatedKafkaListenerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreated> orderCreatedKafkaListenerFactory() {
        JsonDeserializer<OrderCreated> value = new JsonDeserializer<>(OrderCreated.class);
        value.addTrustedPackages("com.store.dto", "*");
        value.ignoreTypeHeaders();

        var cf = new DefaultKafkaConsumerFactory<>(
                baseConsumerProps("inventory-group"), new StringDeserializer(), value);

        var f = new ConcurrentKafkaListenerContainerFactory<String, OrderCreated>();
        f.setConsumerFactory(cf);
        return f;
    }

    @Bean(name = "releaseStockKafkaListenerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, ReleaseStock> releaseStockKafkaListenerFactory() {
        JsonDeserializer<ReleaseStock> value = new JsonDeserializer<>(ReleaseStock.class);
        value.addTrustedPackages("com.store.dto", "*");
        value.ignoreTypeHeaders();

        var cf = new DefaultKafkaConsumerFactory<>(
                baseConsumerProps("inventory-group"), new StringDeserializer(), value);

        var f = new ConcurrentKafkaListenerContainerFactory<String, ReleaseStock>();
        f.setConsumerFactory(cf);
        return f;
    }

    @Bean
    public ConsumerFactory<String, String> productCreatedStringConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092"); // chỉnh theo môi trường của bạn
        // Bỏ qua JsonDeserializer trong YAML bằng cách chỉ định StringDeserializer ở factory này
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // Dùng group hiện tại hoặc tạo group mới để vượt record lỗi cũ
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "inventory-init-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> productCreatedStringFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> f =
                new ConcurrentKafkaListenerContainerFactory<>();
        f.setConsumerFactory(productCreatedStringConsumerFactory());
        f.setConcurrency(2); // tuỳ tải
        return f;
    }

    /* ------------------- PRODUCER (JSON) ------------------- */
    @Bean
    public ProducerFactory<String, Object> inventoryProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> inventoryKafkaTemplate() {
        return new KafkaTemplate<>(inventoryProducerFactory());
    }

    /* ------------------- TOPICS------------------- */
    @Bean
    public NewTopic topicOrderCreated() {
        return TopicBuilder.name("order-created").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic topicStockReserved() {
        return TopicBuilder.name("stock-reserved").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic topicStockRejected() {
        return TopicBuilder.name("stock-rejected").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic topicReleaseStock() {
        return TopicBuilder.name("release-stock").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic topicStockReleased() {
        return TopicBuilder.name("stock-released").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic topicProductStockDecreased() {
        return TopicBuilder.name("product-stock-decreased").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic topicNotifyStockReserved() {
        return TopicBuilder.name("notify-stock-reserved").partitions(1).replicas(1).build();
    }
    @Bean
    public NewTopic topicOrderConfirmed() {
        return TopicBuilder.name("order-confirmed").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic topicOrderCancelled() {
        return TopicBuilder.name("order-cancelled").partitions(1).replicas(1).build();
    }
}
