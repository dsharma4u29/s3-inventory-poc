package com.publicissapient.s3inventory.repository;

import com.publicissapient.s3inventory.config.InventoryProperties;
import com.publicissapient.s3inventory.model.InventoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Repository
public class InventoryRepository {

    private static final Logger log = LoggerFactory.getLogger(InventoryRepository.class);
    private static final int MAX_BATCH_ATTEMPTS = 8;

    private final DynamoDbClient ddb;
    private final String table;
    private final int batchSize;

    public InventoryRepository(DynamoDbClient ddb, InventoryProperties props) {
        this.ddb = ddb;
        this.table = props.tables().inventory();
        this.batchSize = Math.min(25, Math.max(1, props.batchSize()));
    }

    public void putAll(List<InventoryRecord> records) {
        for (int i = 0; i < records.size(); i += batchSize) {
            List<InventoryRecord> chunk = records.subList(i, Math.min(records.size(), i + batchSize));
            writeChunk(chunk);
        }
    }

    private void writeChunk(List<InventoryRecord> chunk) {
        List<WriteRequest> pending = new ArrayList<>(chunk.size());
        for (InventoryRecord r : chunk) {
            Map<String, AttributeValue> item = ItemMapper.toItem(r);
            pending.add(WriteRequest.builder().putRequest(PutRequest.builder().item(item).build()).build());
        }

        for (int attempt = 1; attempt <= MAX_BATCH_ATTEMPTS; attempt++) {
            Map<String, List<WriteRequest>> request = new HashMap<>();
            request.put(table, pending);

            BatchWriteItemResponse response = ddb.batchWriteItem(
                    BatchWriteItemRequest.builder().requestItems(request).build());

            List<WriteRequest> unprocessed = response.unprocessedItems().get(table);
            if (unprocessed == null || unprocessed.isEmpty()) return;

            pending = unprocessed;
            sleepBackoff(attempt, unprocessed.size());
        }
        throw new IllegalStateException(
                "BatchWriteItem left " + pending.size() + " items unprocessed after "
                        + MAX_BATCH_ATTEMPTS + " attempts; page is NOT checkpointed");
    }

    private void sleepBackoff(int attempt, int remaining) {
        long base = Math.min(2000L, 50L * (1L << Math.min(attempt, 5)));
        long jitter = ThreadLocalRandom.current().nextLong(base / 2 + 1);
        log.debug("throttled: {} items unprocessed, attempt {}, sleeping {}ms", remaining, attempt, base + jitter);
        try {
            Thread.sleep(base + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted during batch retry", e);
        }
    }
}
