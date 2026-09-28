package ru.analizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AnalizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalizerApplication.class, args);
    }
}
