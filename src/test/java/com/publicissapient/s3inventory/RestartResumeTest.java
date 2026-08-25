package com.publicissapient.s3inventory;

import com.publicissapient.s3inventory.model.Checkpoint;
import com.publicissapient.s3inventory.model.Shard;
import com.publicissapient.s3inventory.support.FakeS3;
import com.publicissapient.s3inventory.support.FakeStores;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestartResumeTest {

    private static final int OBJECTS = 1000;
    private static final int PAGE = 100;

    @Test
    @DisplayName("crash after inventory, before checkpoint: page replays, nothing is missed")
    void crashBeforeCheckpoint() {
        FakeS3 s3 = new FakeS3().seed(OBJECTS, "sample/");
        FakeStores stores = new FakeStores();
        Shard shard = Shard.whole();

        // Run 1: die right after page 4's records land, before its token is saved.
        int pagesBeforeCrash = runUntil(s3, stores, shard, 4);
        assertEquals(4, pagesBeforeCrash);

        Checkpoint afterCrash = stores.load("job", "all");
        assertEquals(3, afterCrash.pagesProcessed(), "token for the 4th page must NOT be durable");
        assertEquals(400, stores.distinctObjects(), "records for the 4th page ARE durable");

        // Run 2: resume from the last durable token.
        runUntil(s3, stores, shard, Integer.MAX_VALUE);

        assertEquals(OBJECTS, stores.distinctObjects(), "objects missed or duplicated");
        assertEquals(OBJECTS / PAGE, stores.load("job", "all").pagesProcessed());
        assertTrue(stores.totalWrites() > OBJECTS, "expected page 4 to have been replayed");
        assertEquals(OBJECTS + PAGE, stores.totalWrites(), "exactly one page replayed");
    }

    @Test
    @DisplayName("a completed shard is a no-op on restart")
    void restartAfterCompletion() {
        FakeS3 s3 = new FakeS3().seed(OBJECTS, "sample/");
        FakeStores stores = new FakeStores();
        runUntil(s3, stores, Shard.whole(), Integer.MAX_VALUE);
        int writesAfterFirstRun = stores.totalWrites();

        runUntil(s3, stores, Shard.whole(), Integer.MAX_VALUE);
        assertEquals(writesAfterFirstRun, stores.totalWrites(), "completed shard was reprocessed");
    }

    private int runUntil(FakeS3 s3, FakeStores stores, Shard shard, int crashAfterPage) {
        Checkpoint cp = stores.load("job", shard.id());
        if (cp == null) cp = Checkpoint.fresh("job", shard);
        if (cp.status() == com.publicissapient.s3inventory.model.JobStatus.COMPLETED) return 0;

        String token = cp.continuationToken();
        int pagesThisRun = 0;

        while (true) {
            ListObjectsV2Request.Builder req = ListObjectsV2Request.builder()
                    .bucket("bkt").maxKeys(PAGE);
            if (token != null) req.continuationToken(token);
            else if (cp.lastProcessedKey() != null) req.startAfter(cp.lastProcessedKey());

            ListObjectsV2Response page = s3.list(req.build());
            if (page.contents().isEmpty()) {
                stores.save(cp.completed());
                return pagesThisRun;
            }

            stores.putAll(page.contents().stream().map(this::toRecord).toList());
            pagesThisRun++;

            if (pagesThisRun == crashAfterPage) return pagesThisRun;   // <-- the kill

            cp = stores.save(cp.advanced(page.nextContinuationToken(),
                    page.contents().get(page.contents().size() - 1).key(),
                    page.contents().size()));

            if (!Boolean.TRUE.equals(page.isTruncated())) {
                stores.save(cp.completed());
                return pagesThisRun;
            }
            token = page.nextContinuationToken();
        }
    }

    private com.publicissapient.s3inventory.model.InventoryRecord toRecord(S3Object o) {
        return new com.publicissapient.s3inventory.model.InventoryRecord(
                "bkt", o.key(), null, o.size(), o.eTag(), o.lastModified(), "STANDARD",
                null, null, null, null, null, null, null, null, Map.of(), false,
                "job", "run", "all", Instant.now());
    }
}
