package com.example.storeserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class StoreServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreServerApplication.class, args);
    }

}
