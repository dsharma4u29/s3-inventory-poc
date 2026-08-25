package com.publicissapient.s3inventory.support;

import com.publicissapient.s3inventory.config.InventoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FailureInjector {

    private static final Logger log = LoggerFactory.getLogger(FailureInjector.class);

    private final InventoryProperties props;

    public FailureInjector(InventoryProperties props) {
        this.props = props;
    }

    public void maybeCrash(long pageNumber, InventoryProperties.CrashPhase phase) {
        InventoryProperties.Failure f = props.failure();
        if (f == null || !f.enabled()) return;
        if (f.crashPhase() != phase) return;
        if (pageNumber != f.crashAfterPages()) return;

        log.error("INJECTED FAILURE at page {} phase {}", pageNumber, phase);
        throw new InjectedFailureException(pageNumber, phase);
    }

    public static class InjectedFailureException extends RuntimeException {
        public InjectedFailureException(long page, InventoryProperties.CrashPhase phase) {
            super("injected failure: page=" + page + " phase=" + phase);
        }
    }
}
