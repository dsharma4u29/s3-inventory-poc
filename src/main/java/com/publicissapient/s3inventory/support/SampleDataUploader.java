package com.publicissapient.s3inventory.support;

import com.publicissapient.s3inventory.config.InventoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class SampleDataUploader {

    private static final Logger log = LoggerFactory.getLogger(SampleDataUploader.class);

    private final S3Client s3;
    private final InventoryProperties props;

    public SampleDataUploader(S3Client s3, InventoryProperties props) {
        this.s3 = s3;
        this.props = props;
    }

    public void upload() {
        InventoryProperties.Seed seed = props.seed();
        byte[] body = payload(seed.objectSizeBytes());

        try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
            List<Future<?>> tasks = new ArrayList<>(seed.objectCount());
            for (int i = 0; i < seed.objectCount(); i++) {
                String key = String.format("%s%06d.dat", seed.keyPrefix(), i);
                tasks.add(pool.submit(() -> s3.putObject(PutObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        .contentType("application/octet-stream")
                        .metadata(java.util.Map.of("seeded-by", "s3-inventory-poc"))
                        .build(), RequestBody.fromBytes(body))));
            }
            for (Future<?> t : tasks) {
                try {
                    t.get();
                } catch (Exception e) {
                    throw new IllegalStateException("seed upload failed", e);
                }
            }
        }
        log.info("seeded {} objects of {} bytes into s3://{}/{}",
                seed.objectCount(), seed.objectSizeBytes(), props.bucket(), seed.keyPrefix());
    }

    private byte[] payload(int size) {
        StringBuilder sb = new StringBuilder(size);
        while (sb.length() < size) sb.append("s3-inventory-poc-sample-payload.");
        return sb.substring(0, size).getBytes(StandardCharsets.UTF_8);
    }
}
