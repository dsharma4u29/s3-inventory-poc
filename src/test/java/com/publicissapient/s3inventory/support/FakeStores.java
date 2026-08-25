package com.publicissapient.s3inventory.support;

import com.publicissapient.s3inventory.model.Checkpoint;
import com.publicissapient.s3inventory.model.InventoryRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FakeStores {

    public final Map<String, InventoryRecord> inventory = new LinkedHashMap<>();
    public final List<String> writeLog = new ArrayList<>();
    public final Map<String, Checkpoint> checkpoints = new HashMap<>();

    public void putAll(List<InventoryRecord> records) {
        for (InventoryRecord r : records) {
            inventory.put(r.pk() + "|" + r.sk(), r);
            writeLog.add(r.key());
        }
    }

    public Checkpoint save(Checkpoint cp) {
        String k = cp.jobId() + "|" + cp.shardId();
        Checkpoint existing = checkpoints.get(k);
        if (existing != null && existing.version() != cp.version() - 1) {
            throw new IllegalStateException("version conflict");
        }
        checkpoints.put(k, cp);
        return cp;
    }

    public Checkpoint load(String jobId, String shardId) {
        return checkpoints.get(jobId + "|" + shardId);
    }

    public int distinctObjects() {
        return inventory.size();
    }

    public int totalWrites() {
        return writeLog.size();
    }
}
