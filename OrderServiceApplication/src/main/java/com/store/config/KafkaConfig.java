package com.store.config;

import com.store.event.StockRejected;
import com.store.event.StockReserved;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrap;

    @Value("${spring.kafka.consumer.group-id:order-group-v1}")
    private String groupId;

    /* ------------ Producer JSON (gửi Object) ------------ */
    @Bean
    public ProducerFactory<String, Object> orderProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> orderKafkaTemplate() {
        return new KafkaTemplate<>(orderProducerFactory());
    }

    /* ------------ Base consumer props (EH Deserializer) ------------ */
    private Map<String, Object> baseConsumerProps(Class<?> valueType) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // Dùng ErrorHandlingDeserializer + uỷ quyền cho JsonDeserializer
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // Không yêu cầu type headers; set default type theo listener
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.store.dto");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, valueType.getName());

        // Đọc từ bản ghi mới nếu chưa có offset
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return props;
    }

    /* ------------ StockReserved listener factory ------------ */
    @Bean
    public ConsumerFactory<String, StockReserved> stockReservedCF() {
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(StockReserved.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StockReserved>
    stockReservedKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, StockReserved> f = new ConcurrentKafkaListenerContainerFactory<>();
        f.setConsumerFactory(stockReservedCF());
        return f;
    }

    /* ------------ StockRejected listener factory ------------ */
    @Bean
    public ConsumerFactory<String, StockRejected> stockRejectedCF() {
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(StockRejected.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StockRejected>
    stockRejectedKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, StockRejected> f = new ConcurrentKafkaListenerContainerFactory<>();
        f.setConsumerFactory(stockRejectedCF());
        return f;
    }
}
