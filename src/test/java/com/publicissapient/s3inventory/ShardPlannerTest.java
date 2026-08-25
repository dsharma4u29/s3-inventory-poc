package com.publicissapient.s3inventory;

import com.publicissapient.s3inventory.model.Shard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShardPlannerTest {

    @Test
    @DisplayName("shard ranges are half-open, so adjacent shards neither overlap nor gap")
    void rangesTile() {
        Shard a = new Shard("a", "a", "m");
        Shard b = new Shard("b", "m", null);

        assertTrue(a.contains("alpha"));
        assertFalse(a.contains("moon"));
        assertTrue(b.contains("moon"));
        assertTrue(b.contains("zulu"));
        assertTrue(a.isPast("moon"), "shard a must stop at the first key of shard b");
        assertFalse(b.isPast("zzz"), "the final shard has no upper bound");
    }

    @Test
    @DisplayName("the unsharded case covers the whole keyspace")
    void wholeKeyspace() {
        Shard all = Shard.whole();
        assertTrue(all.contains(""));
        assertTrue(all.contains("\uffff"));
        assertFalse(all.isPast("anything"));
    }
}
