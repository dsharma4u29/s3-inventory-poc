package com.publicissapient.s3inventory;

import com.publicissapient.s3inventory.config.InventoryProperties;
import com.publicissapient.s3inventory.model.Shard;
import com.publicissapient.s3inventory.service.InventoryJobRunner;
import com.publicissapient.s3inventory.service.ShardPlanner;
import com.publicissapient.s3inventory.support.SampleDataUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JobCommandLineRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JobCommandLineRunner.class);

    private final InventoryProperties props;
    private final InventoryJobRunner runner;
    private final ShardPlanner shardPlanner;
    private final SampleDataUploader uploader;

    public JobCommandLineRunner(InventoryProperties props,
                                InventoryJobRunner runner,
                                ShardPlanner shardPlanner,
                                SampleDataUploader uploader) {
        this.props = props;
        this.runner = runner;
        this.shardPlanner = shardPlanner;
        this.uploader = uploader;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("seed") || props.seed().enabled()) {
            uploader.upload();
            return;
        }

        List<Shard> shards = shardPlanner.plan();
        log.info("job={} bucket={} shards={}", props.jobId(), props.bucket(), shards.size());

        for (Shard shard : shards) {
            runner.run(props.jobId(), shard);
        }
    }
}
