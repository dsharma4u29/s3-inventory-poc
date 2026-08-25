package com.publicissapient.s3inventory.support;

import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class FakeS3 {

    private final TreeMap<String, Long> objects = new TreeMap<>();
    public int listCalls = 0;

    public FakeS3 seed(int count, String prefix) {
        for (int i = 0; i < count; i++) {
            objects.put(String.format("%s%06d.dat", prefix, i), 1024L);
        }
        return this;
    }

    public int size() {
        return objects.size();
    }

    public ListObjectsV2Response list(ListObjectsV2Request req) {
        listCalls++;
        int maxKeys = req.maxKeys() == null ? 1000 : req.maxKeys();
        String cursor = req.continuationToken() != null ? req.continuationToken() : req.startAfter();

        var tail = cursor == null ? objects.navigableKeySet()
                : objects.navigableKeySet().tailSet(cursor, false);

        List<S3Object> page = new ArrayList<>(maxKeys);
        String last = null;
        for (String key : tail) {
            if (page.size() == maxKeys) break;
            page.add(S3Object.builder()
                    .key(key)
                    .size(objects.get(key))
                    .eTag("\"fake-" + key.hashCode() + "\"")
                    .lastModified(Instant.parse("2026-01-01T00:00:00Z"))
                    .storageClass("STANDARD")
                    .build());
            last = key;
        }
        boolean truncated = last != null && objects.higherKey(last) != null;
        return ListObjectsV2Response.builder()
                .contents(page)
                .keyCount(page.size())
                .isTruncated(truncated)
                .nextContinuationToken(truncated ? last : null)
                .build();
    }
}
