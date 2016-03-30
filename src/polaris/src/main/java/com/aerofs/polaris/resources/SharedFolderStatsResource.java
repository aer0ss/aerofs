package com.aerofs.polaris.resources;


public final class SharedFolderStatsResource {

    public final Long maxFileCount;
    public final Long avgFileCount;

    public SharedFolderStatsResource(Long maxFileCount, Long avgFileCount) {
        this.maxFileCount = maxFileCount;
        this.avgFileCount = avgFileCount;
    }
}
