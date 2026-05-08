package com.thesis.sentimentshop.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = "com.thesis.sentimentshop")
@EntityScan(basePackages = "com.thesis.sentimentshop")
@EnableJpaRepositories(basePackages = "com.thesis.sentimentshop")
public class SentimentShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentimentShopApplication.class, args);
    }
}
