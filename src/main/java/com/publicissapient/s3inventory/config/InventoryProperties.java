package com.publicissapient.s3inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "inventory")
public record InventoryProperties(
        String bucket,
        String jobId,
        String region,
        Tables tables,
        int pageSize,
        List<ShardSpec> shards,
        HeadMode headMode,
        int headConcurrency,
        Set<HeadField> headRequiredFields,
        int batchSize,
        int writeConcurrency,
        Failure failure,
        Seed seed
) {
    public record Tables(String inventory, String checkpoint) {}

    public record ShardSpec(String id, String startInclusive, String endExclusive) {}

    public record Failure(boolean enabled, int crashAfterPages, CrashPhase crashPhase) {}

    public record Seed(boolean enabled, int objectCount, int objectSizeBytes, String keyPrefix) {}

    public enum CrashPhase {
        AFTER_INVENTORY_BEFORE_CHECKPOINT,
        MID_INVENTORY_WRITE,
        AFTER_CHECKPOINT
    }
}
