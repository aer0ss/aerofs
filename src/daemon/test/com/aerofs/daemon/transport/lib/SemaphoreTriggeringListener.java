/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;

// keep non-final so that Mockito can spy on it
public class SemaphoreTriggeringListener implements IUnicastListener
{
    private static final Logger l = LoggerFactory.getLogger(SemaphoreTriggeringListener.class);

    public final Semaphore unicastUnavailableSemaphore = new Semaphore(0);
    public final Semaphore deviceConnectedSemaphore = new Semaphore(0);
    public final Semaphore deviceDisconnectedSemaphore = new Semaphore(0);

    @Override
    public void onUnicastReady()
    {
        // noop
    }

    @Override
    public void onUnicastUnavailable()
    {
        unicastUnavailableSemaphore.release();
        l.debug("released unicast unavailable semaphore");
    }

    @Override
    public void onDeviceConnected(DID did)
    {
        deviceConnectedSemaphore.release();
        l.debug("released device connected semaphore");
    }

    @Override
    public void onDeviceDisconnected(DID did)
    {
        deviceDisconnectedSemaphore.release();
        l.debug("released device disconnected semaphore");
    }
}
