package com.publicissapient.s3inventory.model;

import java.time.Instant;
import java.util.Map;

public record InventoryRecord(
        String bucket,
        String key,
        String versionId,
        long size,
        String eTag,
        Instant lastModified,
        String storageClass,
        String contentType,
        String cacheControl,
        String sseAlgorithm,
        String kmsKeyId,
        String checksumAlgorithm,
        String objectLockMode,
        Instant objectLockRetainUntil,
        String objectLockLegalHold,
        Map<String, String> userMetadata,
        boolean headFetched,
        String jobId,
        String runId,
        String shardId,
        Instant observedAt
) {
    public static final String NO_VERSION = "null";

    public String pk() {
        return "B#" + bucket + "#K#" + key;
    }

    public String sk() {
        return versionId == null || versionId.isBlank() ? NO_VERSION : versionId;
    }
}
