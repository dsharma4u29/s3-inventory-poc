package com.publicissapient.s3inventory.service;

import com.publicissapient.s3inventory.config.InventoryProperties;
import com.publicissapient.s3inventory.model.Checkpoint;
import com.publicissapient.s3inventory.model.InventoryRecord;
import com.publicissapient.s3inventory.model.JobStatus;
import com.publicissapient.s3inventory.model.Shard;
import com.publicissapient.s3inventory.repository.CheckpointRepository;
import com.publicissapient.s3inventory.repository.InventoryRepository;
import com.publicissapient.s3inventory.support.FailureInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryJobRunner {

    private static final Logger log = LoggerFactory.getLogger(InventoryJobRunner.class);

    private final ObjectLister lister;
    private final HeadEnricher enricher;
    private final InventoryRepository inventory;
    private final CheckpointRepository checkpoints;
    private final FailureInjector failures;
    private final InventoryProperties props;

    public InventoryJobRunner(ObjectLister lister,
                              HeadEnricher enricher,
                              InventoryRepository inventory,
                              CheckpointRepository checkpoints,
                              FailureInjector failures,
                              InventoryProperties props) {
        this.lister = lister;
        this.enricher = enricher;
        this.inventory = inventory;
        this.checkpoints = checkpoints;
        this.failures = failures;
        this.props = props;
    }

    public Checkpoint run(String jobId, Shard shard) {
        String runId = UUID.randomUUID().toString();
        Checkpoint cp = checkpoints.loadOrCreate(jobId, shard);

        if (cp.status() == JobStatus.COMPLETED) {
            log.info("shard={} already complete ({} objects); nothing to do", shard.id(), cp.objectsProcessed());
            return cp;
        }
        if (cp.isResumable()) {
            log.info("resuming shard={} after page {} (lastKey={})",
                    shard.id(), cp.pagesProcessed(), cp.lastProcessedKey());
        }

        String token = cp.continuationToken();
        while (true) {
            ListObjectsV2Response page = lister.list(shard, token, cp.lastProcessedKey());

            List<S3Object> inRange = new ArrayList<>(page.contents().size());
            boolean pastShard = false;
            for (S3Object o : page.contents()) {
                if (shard.isPast(o.key())) { pastShard = true; break; }
                if (shard.contains(o.key())) inRange.add(o);
            }

            if (!inRange.isEmpty()) {
                failures.maybeCrash(cp.pagesProcessed() + 1,
                        InventoryProperties.CrashPhase.MID_INVENTORY_WRITE);

                List<InventoryRecord> records = enricher.enrich(inRange, shard, runId);
                inventory.putAll(records);          // (2) durable inventory

                failures.maybeCrash(cp.pagesProcessed() + 1,
                        InventoryProperties.CrashPhase.AFTER_INVENTORY_BEFORE_CHECKPOINT);

                cp = checkpoints.save(cp.advanced(   // (3) durable progress
                        page.nextContinuationToken(),
                        inRange.get(inRange.size() - 1).key(),
                        records.size()));

                failures.maybeCrash(cp.pagesProcessed(),
                        InventoryProperties.CrashPhase.AFTER_CHECKPOINT);

                if (cp.pagesProcessed() % 25 == 0) {
                    log.info("shard={} pages={} objects={}", shard.id(),
                            cp.pagesProcessed(), cp.objectsProcessed());
                }
            }

            boolean done = pastShard || !Boolean.TRUE.equals(page.isTruncated());
            if (done) {
                cp = checkpoints.save(cp.completed());
                log.info("shard={} COMPLETE pages={} objects={} head={}",
                        shard.id(), cp.pagesProcessed(), cp.objectsProcessed(), enricher.headEnabled());
                return cp;
            }
            token = page.nextContinuationToken();
        }
    }
}
