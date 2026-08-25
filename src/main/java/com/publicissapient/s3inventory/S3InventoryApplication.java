package com.publicissapient.s3inventory;

import com.publicissapient.s3inventory.config.InventoryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(InventoryProperties.class)
public class S3InventoryApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(S3InventoryApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        System.exit(SpringApplication.exit(app.run(args)));
    }
}
