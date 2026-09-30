package ru.analizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class AnalizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalizerApplication.class, args);
    }

    /**
     * Часы внедряются, а не берутся напрямую: иначе «сегодня» в тестах нельзя было бы
     * подделать, а проверка окончательности дня целиком на этом и держится.
     */
    @Configuration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }
    }
}
