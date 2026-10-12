package com.splitwise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SplitwiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(SplitwiseApplication.class, args);
    }
}
