package com.store.config;

import com.store.dto.OrderDTO;
import com.store.dto.PaymentFailed;
import com.store.dto.PaymentSucceeded;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    /* ---------- CONSUMER (OrderDTO) ---------- */
    @Bean
    public ConsumerFactory<String, OrderDTO> orderPlacedEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Không dùng type headers, mặc định map vào OrderDTO
        JsonDeserializer<OrderDTO> deserializer = new JsonDeserializer<>(OrderDTO.class, false);
        deserializer.addTrustedPackages("com.store.dto", "*");
        deserializer.setUseTypeMapperForKey(false);

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderDTO> orderPlacedEventListenerFactory(
            ConsumerFactory<String, OrderDTO> consumerFactory,
            DefaultErrorHandler defaultErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, OrderDTO> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(defaultErrorHandler);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, PaymentSucceeded> paymentSucceededConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        JsonDeserializer<PaymentSucceeded> deserializer = new JsonDeserializer<>(PaymentSucceeded.class, false);
        deserializer.addTrustedPackages("com.store.dto", "*");
        deserializer.setUseTypeMapperForKey(false);

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentSucceeded> paymentSucceededListenerFactory(
            ConsumerFactory<String, PaymentSucceeded> cf) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentSucceeded> f = new ConcurrentKafkaListenerContainerFactory<>();
        f.setConsumerFactory(cf);
        return f;
    }
    @Bean
    public ConsumerFactory<String, PaymentFailed> paymentFailedConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        JsonDeserializer<PaymentFailed> deserializer = new JsonDeserializer<>(PaymentFailed.class, false);
        deserializer.addTrustedPackages("com.store.dto", "*");
        deserializer.setUseTypeMapperForKey(false);

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailed> paymentFailedListenerFactory(
            ConsumerFactory<String, PaymentFailed> cf) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentFailed> f =
                new ConcurrentKafkaListenerContainerFactory<>();
        f.setConsumerFactory(cf);
        return f;
    }


    /* ---------- PRODUCER (JsonSerializer) cho DLT ---------- */
    @Bean
    public ProducerFactory<String, OrderDTO> orderDtoProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Không thêm type headers để tương thích với consumer ở trên
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, OrderDTO> orderDtoKafkaTemplate() {
        return new KafkaTemplate<>(orderDtoProducerFactory());
    }

    /* ---------- ErrorHandler + DeadLetterPublishingRecoverer ---------- */
    @Bean
    public DefaultErrorHandler defaultErrorHandler(KafkaTemplate<String, OrderDTO> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + "-retry-2000", record.partition())
        );
        // Không retry tại chỗ; đẩy thẳng DLT. (Tuỳ bạn chỉnh backoff)
        return new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 0L));
    }

    /* ---------- Topics ---------- */
    @Bean
    public NewTopic orderTopic() {
        return TopicBuilder.name("order-topic")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderTopicRetry2000() {
        return TopicBuilder.name("order-topic-retry-2000")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
