package com.aerofs.daemon.core.net;

public interface Timable<T> extends Comparable<T> {
    void timeout_();
}
