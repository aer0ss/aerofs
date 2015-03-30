package com.aerofs.daemon.lib.db.trans;

public interface TransBoundaryChecker {
    void assertNoOngoingTransaction_();
}
