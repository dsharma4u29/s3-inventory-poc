package com.publicissapient.s3inventory.model;

public record Shard(String id, String startInclusive, String endExclusive) {

    public static Shard whole() {
        return new Shard("all", "", null);
    }

    public boolean contains(String key) {
        if (startInclusive != null && key.compareTo(startInclusive) < 0) return false;
        return endExclusive == null || key.compareTo(endExclusive) < 0;
    }

    public boolean isPast(String key) {
        return endExclusive != null && key.compareTo(endExclusive) >= 0;
    }
}
