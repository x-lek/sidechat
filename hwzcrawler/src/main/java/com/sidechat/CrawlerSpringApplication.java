package com.sidechat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CrawlerSpringApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrawlerSpringApplication.class, args);
    }
}
