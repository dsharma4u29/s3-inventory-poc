package com.publicissapient.s3inventory.repository;

import com.publicissapient.s3inventory.model.Checkpoint;
import com.publicissapient.s3inventory.model.InventoryRecord;
import com.publicissapient.s3inventory.model.JobStatus;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class ItemMapper {

    private ItemMapper() {}

    public static Map<String, AttributeValue> toItem(InventoryRecord r) {
        Map<String, AttributeValue> item = new HashMap<>();
        put(item, "pk", r.pk());
        put(item, "sk", r.sk());
        put(item, "bucket", r.bucket());
        put(item, "objectKey", r.key());
        item.put("size", num(r.size()));
        put(item, "etag", r.eTag());
        put(item, "lastModified", r.lastModified());
        put(item, "storageClass", r.storageClass());
        put(item, "contentType", r.contentType());
        put(item, "cacheControl", r.cacheControl());
        put(item, "sseAlgorithm", r.sseAlgorithm());
        put(item, "kmsKeyId", r.kmsKeyId());
        put(item, "checksumAlgorithm", r.checksumAlgorithm());
        put(item, "objectLockMode", r.objectLockMode());
        put(item, "objectLockRetainUntil", r.objectLockRetainUntil());
        put(item, "objectLockLegalHold", r.objectLockLegalHold());
        item.put("headFetched", AttributeValue.fromBool(r.headFetched()));
        put(item, "jobId", r.jobId());
        put(item, "runId", r.runId());
        put(item, "shardId", r.shardId());
        put(item, "observedAt", r.observedAt());
        if (r.userMetadata() != null && !r.userMetadata().isEmpty()) {
            Map<String, AttributeValue> md = new HashMap<>();
            r.userMetadata().forEach((k, v) -> md.put(k, AttributeValue.fromS(v)));
            item.put("userMetadata", AttributeValue.fromM(md));
        }
        return item;
    }

    public static Map<String, AttributeValue> toItem(Checkpoint c) {
        Map<String, AttributeValue> item = new HashMap<>();
        put(item, "jobId", c.jobId());
        put(item, "shardId", c.shardId());
        put(item, "continuationToken", c.continuationToken());
        put(item, "lastProcessedKey", c.lastProcessedKey());
        item.put("pagesProcessed", num(c.pagesProcessed()));
        item.put("objectsProcessed", num(c.objectsProcessed()));
        put(item, "status", c.status().name());
        item.put("version", num(c.version()));
        put(item, "updatedAt", c.updatedAt());
        return item;
    }

    public static Checkpoint toCheckpoint(Map<String, AttributeValue> item) {
        return new Checkpoint(
                str(item, "jobId"),
                str(item, "shardId"),
                str(item, "continuationToken"),
                str(item, "lastProcessedKey"),
                longOf(item, "pagesProcessed"),
                longOf(item, "objectsProcessed"),
                JobStatus.valueOf(item.containsKey("status")
                        ? item.get("status").s() : JobStatus.PENDING.name()),
                longOf(item, "version"),
                item.containsKey("updatedAt")
                        ? Instant.parse(item.get("updatedAt").s()) : Instant.EPOCH);
    }

    private static void put(Map<String, AttributeValue> m, String k, String v) {
        if (v != null && !v.isEmpty()) m.put(k, AttributeValue.fromS(v));
    }

    private static void put(Map<String, AttributeValue> m, String k, Instant v) {
        if (v != null) m.put(k, AttributeValue.fromS(v.toString()));
    }

    private static AttributeValue num(long v) {
        return AttributeValue.fromN(Long.toString(v));
    }

    private static String str(Map<String, AttributeValue> m, String k) {
        AttributeValue v = m.get(k);
        return v == null ? null : v.s();
    }

    private static long longOf(Map<String, AttributeValue> m, String k) {
        AttributeValue v = m.get(k);
        return v == null ? 0L : Long.parseLong(v.n());
    }
}
