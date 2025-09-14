package com.store.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

//    @Bean
//    @LoadBalanced
//    public RestTemplate restTemplate() {
//        var factory = new SimpleClientHttpRequestFactory();
//        factory.setConnectTimeout(4000);
//        factory.setReadTimeout(8000);
//        RestTemplate rt = new RestTemplate(factory);
//        return rt;
//    }
}