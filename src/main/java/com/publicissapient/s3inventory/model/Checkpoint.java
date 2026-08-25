package com.publicissapient.s3inventory.model;

import java.time.Instant;

public record Checkpoint(
        String jobId,
        String shardId,
        String continuationToken,
        String lastProcessedKey,
        long pagesProcessed,
        long objectsProcessed,
        JobStatus status,
        long version,
        Instant updatedAt
) {
    public static Checkpoint fresh(String jobId, Shard shard) {
        return new Checkpoint(jobId, shard.id(), null, null, 0, 0,
                JobStatus.PENDING, 0, Instant.now());
    }

    public Checkpoint advanced(String token, String lastKey, int objectsInPage) {
        return new Checkpoint(jobId, shardId, token, lastKey,
                pagesProcessed + 1, objectsProcessed + objectsInPage,
                JobStatus.IN_PROGRESS, version + 1, Instant.now());
    }

    public Checkpoint completed() {
        return new Checkpoint(jobId, shardId, null, lastProcessedKey,
                pagesProcessed, objectsProcessed,
                JobStatus.COMPLETED, version + 1, Instant.now());
    }

    public boolean isResumable() {
        return continuationToken != null || lastProcessedKey != null;
    }
}
