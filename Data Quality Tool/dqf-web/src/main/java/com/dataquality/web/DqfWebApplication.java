package com.dataquality.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DqfWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(DqfWebApplication.class, args);
    }
}