package org.unreal.modelrouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ModelRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelRouterApplication.class, args);
    }

}