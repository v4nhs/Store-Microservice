package com.store.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpClientConfig {
    @Bean
    @Primary
    @LoadBalanced
    public RestTemplate lbRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
    @Bean
    @Qualifier("externalRestTemplate")
    public RestTemplate externalRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
