package org.unreal.modelrouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan("org.unreal.modelrouter.config")
public class ModelRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelRouterApplication.class, args);
    }

}