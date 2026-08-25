package com.publicissapient.s3inventory.repository;

import com.publicissapient.s3inventory.config.InventoryProperties;
import com.publicissapient.s3inventory.model.Checkpoint;
import com.publicissapient.s3inventory.model.Shard;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

@Repository
public class CheckpointRepository {

    private final DynamoDbClient ddb;
    private final String table;

    public CheckpointRepository(DynamoDbClient ddb, InventoryProperties props) {
        this.ddb = ddb;
        this.table = props.tables().checkpoint();
    }

    public Checkpoint loadOrCreate(String jobId, Shard shard) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("jobId", AttributeValue.fromS(jobId));
        key.put("shardId", AttributeValue.fromS(shard.id()));

        GetItemResponse res = ddb.getItem(GetItemRequest.builder()
                .tableName(table)
                .key(key)
                .consistentRead(true)   // resume MUST NOT read a stale token
                .build());

        if (res.hasItem() && !res.item().isEmpty()) {
            return ItemMapper.toCheckpoint(res.item());
        }
        return Checkpoint.fresh(jobId, shard);
    }

    public Checkpoint save(Checkpoint next) {
        Map<String, String> names = new HashMap<>();
        names.put("#v", "version");
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":prev", AttributeValue.fromN(Long.toString(next.version() - 1)));

        try {
            ddb.putItem(PutItemRequest.builder()
                    .tableName(table)
                    .item(ItemMapper.toItem(next))
                    .conditionExpression("attribute_not_exists(#v) OR #v = :prev")
                    .expressionAttributeNames(names)
                    .expressionAttributeValues(values)
                    .build());
            return next;
        } catch (ConditionalCheckFailedException e) {
            throw new ConcurrentShardOwnershipException(next.jobId(), next.shardId(), e);
        }
    }

    public static class ConcurrentShardOwnershipException extends RuntimeException {
        public ConcurrentShardOwnershipException(String jobId, String shardId, Throwable cause) {
            super("checkpoint version conflict for job=" + jobId + " shard=" + shardId
                    + ": another worker is processing this shard", cause);
        }
    }
}
