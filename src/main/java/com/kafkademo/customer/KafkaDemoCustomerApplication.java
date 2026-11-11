package com.kafkademo.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KafkaDemoCustomerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaDemoCustomerApplication.class, args);
    }
}
