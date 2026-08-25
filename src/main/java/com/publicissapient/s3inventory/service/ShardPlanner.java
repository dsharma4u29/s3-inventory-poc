package com.publicissapient.s3inventory.service;

import com.publicissapient.s3inventory.config.InventoryProperties;
import com.publicissapient.s3inventory.model.Shard;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import java.util.ArrayList;
import java.util.List;

@Service
public class ShardPlanner {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";

    private final S3Client s3;
    private final InventoryProperties props;

    public ShardPlanner(S3Client s3, InventoryProperties props) {
        this.s3 = s3;
        this.props = props;
    }

    public List<Shard> plan() {
        if (props.shards() != null && !props.shards().isEmpty()) {
            return props.shards().stream()
                    .map(s -> new Shard(s.id(), s.startInclusive(), s.endExclusive()))
                    .toList();
        }
        return List.of(Shard.whole());
    }

    public List<Shard> planByCommonPrefixes(String delimiter) {
        List<String> prefixes = new ArrayList<>();
        String token = null;
        do {
            ListObjectsV2Response res = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(props.bucket())
                    .delimiter(delimiter)
                    .continuationToken(token)
                    .build());
            res.commonPrefixes().forEach(p -> prefixes.add(p.prefix()));
            token = res.nextContinuationToken();
        } while (token != null);

        List<Shard> shards = new ArrayList<>(prefixes.size());
        for (int i = 0; i < prefixes.size(); i++) {
            String start = prefixes.get(i);
            String end = i + 1 < prefixes.size() ? prefixes.get(i + 1) : null;
            shards.add(new Shard("p" + i, start, end));
        }
        return shards;
    }

    public List<Shard> planByAlphabet(String prefix) {
        List<Shard> shards = new ArrayList<>(ALPHABET.length());
        for (int i = 0; i < ALPHABET.length(); i++) {
            String start = prefix + ALPHABET.charAt(i);
            String end = i + 1 < ALPHABET.length() ? prefix + ALPHABET.charAt(i + 1) : null;
            shards.add(new Shard("a" + ALPHABET.charAt(i), start, end));
        }
        return shards;
    }
}
