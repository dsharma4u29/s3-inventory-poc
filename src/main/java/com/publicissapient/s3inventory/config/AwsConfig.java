package com.publicissapient.s3inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.FullJitterBackoffStrategy;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

@Configuration
public class AwsConfig {

    @Bean
    public S3Client s3Client(InventoryProperties props) {
        return S3Client.builder()
                .region(Region.of(props.region()))
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(Math.max(64, props.headConcurrency() * 2))
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(30)))
                .overrideConfiguration(retryConfig(10))
                .build();
    }

    @Bean
    public DynamoDbClient dynamoDbClient(InventoryProperties props) {
        return DynamoDbClient.builder()
                .region(Region.of(props.region()))
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(Math.max(32, props.writeConcurrency() * 4)))
                .overrideConfiguration(retryConfig(8))
                .build();
    }

    private ClientOverrideConfiguration retryConfig(int retries) {
        return ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder()
                        .numRetries(retries)
                        .backoffStrategy(FullJitterBackoffStrategy.builder()
                                .baseDelay(Duration.ofMillis(50))
                                .maxBackoffTime(Duration.ofSeconds(20))
                                .build())
                        .build())
                .apiCallTimeout(Duration.ofMinutes(2))
                .build();
    }
}
