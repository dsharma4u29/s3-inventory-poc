package com.publicissapient.s3inventory.service;

import com.publicissapient.s3inventory.config.InventoryProperties;
import com.publicissapient.s3inventory.model.Shard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class ObjectLister {

    private static final Logger log = LoggerFactory.getLogger(ObjectLister.class);

    private final S3Client s3;
    private final InventoryProperties props;

    public ObjectLister(S3Client s3, InventoryProperties props) {
        this.s3 = s3;
        this.props = props;
    }

    public ListObjectsV2Response list(Shard shard, String token, String lastKey) {
        try {
            return s3.listObjectsV2(request(shard, token, lastKey));
        } catch (S3Exception e) {
            if (!isInvalidToken(e) || lastKey == null) throw e;
            log.warn("continuation token rejected for shard={}, falling back to startAfter={}",
                    shard.id(), lastKey);
            return s3.listObjectsV2(request(shard, null, lastKey));
        }
    }

    private ListObjectsV2Request request(Shard shard, String token, String lastKey) {
        ListObjectsV2Request.Builder b = ListObjectsV2Request.builder()
                .bucket(props.bucket())
                .maxKeys(Math.min(1000, props.pageSize()));

        if (token != null) {
            b.continuationToken(token);
        } else if (lastKey != null) {
            b.startAfter(lastKey);
        } else if (shard.startInclusive() != null && !shard.startInclusive().isEmpty()) {
            // startAfter is exclusive; step back one code point to keep the
            // shard's first key inside the range.
            b.startAfter(predecessorOf(shard.startInclusive()));
        }
        return b.build();
    }

    private static String predecessorOf(String key) {
        return key.substring(0, key.length() - 1)
                + (char) (key.charAt(key.length() - 1) - 1)
                + Character.toString(Character.MAX_VALUE);
    }

    private static boolean isInvalidToken(S3Exception e) {
        String code = e.awsErrorDetails() == null ? "" : e.awsErrorDetails().errorCode();
        return "InvalidArgument".equals(code) || "InvalidToken".equals(code);
    }
}
