package com.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MigrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigrationServiceApplication.class, args);
    }
}
