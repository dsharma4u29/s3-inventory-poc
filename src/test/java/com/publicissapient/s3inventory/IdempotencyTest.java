package com.publicissapient.s3inventory;

import com.publicissapient.s3inventory.model.InventoryRecord;
import com.publicissapient.s3inventory.support.FakeStores;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class IdempotencyTest {

    private InventoryRecord record(String key, String runId) {
        return new InventoryRecord("bkt", key, null, 1024L, "etag",
                Instant.parse("2026-01-01T00:00:00Z"), "STANDARD",
                null, null, null, null, null, null, null, null, Map.of(), false,
                "job", runId, "all", Instant.now());
    }

    @Test
    @DisplayName("replaying a page rewrites the same items, never new ones")
    void replayDoesNotDuplicate() {
        FakeStores stores = new FakeStores();
        List<InventoryRecord> page = List.of(record("a", "run-1"), record("b", "run-1"));

        stores.putAll(page);
        stores.putAll(page);                       // the retry
        stores.putAll(List.of(record("a", "run-2"), record("b", "run-2")));  // a later run

        assertEquals(2, stores.distinctObjects(), "duplicate rows created");
        assertEquals(6, stores.totalWrites(), "expected three write passes");
    }

    @Test
    @DisplayName("versionId participates in the key, so versions do not collide")
    void versionsAreSeparateItems() {
        InventoryRecord v1 = new InventoryRecord("bkt", "a", "v1", 1L, "e", Instant.EPOCH,
                "STANDARD", null, null, null, null, null, null, null, null, Map.of(), true,
                "job", "run", "all", Instant.now());
        InventoryRecord v2 = new InventoryRecord("bkt", "a", "v2", 1L, "e", Instant.EPOCH,
                "STANDARD", null, null, null, null, null, null, null, null, Map.of(), true,
                "job", "run", "all", Instant.now());

        assertEquals(v1.pk(), v2.pk());
        assertNotEquals(v1.sk(), v2.sk());
    }

    @Test
    @DisplayName("unversioned objects get a stable sentinel sort key")
    void unversionedSentinel() {
        assertEquals(InventoryRecord.NO_VERSION, record("a", "r").sk());
    }
}
