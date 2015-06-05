package com.aerofs.daemon.transport;

public interface ISignallingServiceFactory {
    ISignallingService newSignallingService(String id);
}
