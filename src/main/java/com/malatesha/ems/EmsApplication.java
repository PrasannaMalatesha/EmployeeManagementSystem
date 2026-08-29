package com.malatesha.ems;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Boot entry point for the EMS modular monolith. Component scanning starts from
 * this package, so every feature module under {@code com.malatesha.ems.*} is
 * wired in. {@code @EnableJpaAuditing} powers the {@code @CreatedDate}/
 * {@code @LastModifiedDate} auditing fields on entities.
 */
@SpringBootApplication
@EnableJpaAuditing
public class EmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmsApplication.class, args);
    }
}
