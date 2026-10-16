package com.clubbouncer;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ClubBouncerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClubBouncerApplication.class, args);
    }

    /** Time comes from a Clock bean, so tests can replace it and control time exactly. */
    @Bean
    Clock clock() { return Clock.systemUTC(); }
}
