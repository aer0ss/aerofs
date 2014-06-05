package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.lib.Util;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

public class MaxcastFilterSender
{
    private static final Logger l = Loggers.getLogger(MaxcastFilterSender.class);

    private final AtomicInteger lastMaxcastId = new AtomicInteger(Util.rand().nextInt());

    public int getNextMaxcastId()
    {
        int nextMaxcastId = lastMaxcastId.incrementAndGet();
        l.trace("generate new maxcast id {}", nextMaxcastId);
        return nextMaxcastId;
    }
}
