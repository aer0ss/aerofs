package com.aerofs.rest.api;

import java.util.Collection;

public class Page<T> {
    public final boolean hasMore;
    public final Collection<T> data;

    public Page(boolean hasMore, Collection<T> data) {
        this.hasMore = hasMore;
        this.data = data;
    }
}
