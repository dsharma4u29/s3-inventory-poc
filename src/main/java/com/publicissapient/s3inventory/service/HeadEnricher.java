package com.publicissapient.s3inventory.service;

import com.publicissapient.s3inventory.config.HeadField;
import com.publicissapient.s3inventory.config.InventoryProperties;
import com.publicissapient.s3inventory.model.InventoryRecord;
import com.publicissapient.s3inventory.model.Shard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Service
public class HeadEnricher {

    private static final Logger log = LoggerFactory.getLogger(HeadEnricher.class);
    private static final Set<HeadField> HEAD_ONLY = Set.of(HeadField.values());

    private final S3Client s3;
    private final InventoryProperties props;
    private final Semaphore inFlight;

    public HeadEnricher(S3Client s3, InventoryProperties props) {
        this.s3 = s3;
        this.props = props;
        this.inFlight = new Semaphore(Math.max(1, props.headConcurrency()));
    }

    public boolean headEnabled() {
        return switch (props.headMode()) {
            case ALWAYS -> true;
            case NEVER -> false;
            case ON_DEMAND -> props.headRequiredFields() != null
                    && props.headRequiredFields().stream().anyMatch(HEAD_ONLY::contains);
        };
    }

    public List<InventoryRecord> enrich(List<S3Object> objects, Shard shard, String runId) {
        if (!headEnabled()) {
            return objects.stream().map(o -> fromList(o, shard, runId)).toList();
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(
                Math.max(1, props.headConcurrency()),
                Thread.ofPlatform().name("head-", 0).factory())) {

            List<java.util.concurrent.Future<InventoryRecord>> futures = objects.stream()
                    .map(o -> pool.submit(() -> withHead(o, shard, runId)))
                    .toList();

            List<InventoryRecord> out = new java.util.ArrayList<>(objects.size());
            for (var future : futures) {
                try {
                    out.add(future.get());
                } catch (Exception e) {
                    throw new IllegalStateException("HEAD fan-out failed; page not checkpointed", e);
                }
            }
            return out;
        }
    }

    private InventoryRecord withHead(S3Object o, Shard shard, String runId) {
        try {
            inFlight.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        try {
            HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(o.key())
                    .build());
            return merge(o, head, shard, runId);
        } catch (NoSuchKeyException deletedMidScan) {
            log.debug("object {} disappeared between LIST and HEAD", o.key());
            return fromList(o, shard, runId);
        } finally {
            inFlight.release();
        }
    }

    private InventoryRecord fromList(S3Object o, Shard shard, String runId) {
        return new InventoryRecord(
                props.bucket(), o.key(), null,
                o.size() == null ? 0L : o.size(),
                strip(o.eTag()), o.lastModified(),
                o.storageClassAsString(),
                null, null, null, null, null, null, null, null, Map.of(), false,
                props.jobId(), runId, shard.id(), Instant.now());
    }

    private InventoryRecord merge(S3Object o, HeadObjectResponse h, Shard shard, String runId) {
        return new InventoryRecord(
                props.bucket(), o.key(), h.versionId(),
                h.contentLength() == null ? (o.size() == null ? 0L : o.size()) : h.contentLength(),
                strip(h.eTag() != null ? h.eTag() : o.eTag()),
                h.lastModified() != null ? h.lastModified() : o.lastModified(),
                h.storageClassAsString() != null ? h.storageClassAsString() : o.storageClassAsString(),
                h.contentType(),
                h.cacheControl(),
                h.serverSideEncryptionAsString(),
                h.ssekmsKeyId(),
                h.checksumSHA256() == null || h.checksumSHA256().isEmpty()
                        ? null : h.checksumSHA256().toString(),
                h.objectLockModeAsString(),
                h.objectLockRetainUntilDate(),
                h.objectLockLegalHoldStatusAsString(),
                h.metadata() == null ? Map.of() : h.metadata(),
                true,
                props.jobId(), runId, shard.id(), Instant.now());
    }

    private static String strip(String etag) {
        return etag == null ? null : etag.replace("\"", "");
    }
}
