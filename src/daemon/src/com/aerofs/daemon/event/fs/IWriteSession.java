package com.aerofs.daemon.event.fs;

public interface IWriteSession {

    // return true if an IEIPreWrite event must be executed before proceeding
    // to actual write.
    boolean preWrite();
}
