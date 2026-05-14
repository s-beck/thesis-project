package com.thesis.sentimentshop.web;

import com.thesis.sentimentshop.inference.measurement.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@SpringBootApplication
@ComponentScan(basePackages = "com.thesis.sentimentshop")
@EntityScan(basePackages = "com.thesis.sentimentshop")
@EnableJpaRepositories(basePackages = "com.thesis.sentimentshop")
@EnableScheduling
public class SentimentShopApplication {
    private static final Instant START_INSTANT = Instant.now();

    public static void main(String[] args) {
        SpringApplication.run(SentimentShopApplication.class, args);
    }
    @Component
    static class StartupEmitter {
        @EventListener
        public void onReady(ApplicationReadyEvent event) {
            long startupMs = Duration.between(START_INSTANT, Instant.now()).toMillis();
            MeasurementLog.startup(startupMs);
        }
    }
}
