package com.store.config;

import com.store.dto.StockRejected;
import com.store.dto.StockReserved;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfigOrder {

    // ===== Producer JSON (gửi Object) =====
    @Bean
    public ProducerFactory<String, Object> orderProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // tuỳ chọn:
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> orderKafkaTemplate() {
        return new KafkaTemplate<>(orderProducerFactory());
    }

    // ===== Base consumer props =====
    private Map<String, Object> baseConsumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // cho phép deserialize các DTO trong package của bạn
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.store.dto");
        // không bắt buộc type header
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return props;
    }

    // ===== Consumer factory cho StockReserved =====
    @Bean
    public ConsumerFactory<String, StockReserved> stockReservedCF() {
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps("order-group"),
                new StringDeserializer(),
                new JsonDeserializer<>(StockReserved.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StockReserved>
    stockReservedKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, StockReserved> f = new ConcurrentKafkaListenerContainerFactory<>();
        f.setConsumerFactory(stockReservedCF());
        return f;
    }

    // ===== Consumer factory cho StockRejected =====
    @Bean
    public ConsumerFactory<String, StockRejected> stockRejectedCF() {
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps("order-group"),
                new StringDeserializer(),
                new JsonDeserializer<>(StockRejected.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StockRejected>
    stockRejectedKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, StockRejected> f = new ConcurrentKafkaListenerContainerFactory<>();
        f.setConsumerFactory(stockRejectedCF());
        return f;
    }
}
