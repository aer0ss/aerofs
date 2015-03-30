/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.net.throttling.GlobalLimiter;
import com.aerofs.daemon.core.net.throttling.LimitMonitor;
import com.google.inject.Inject;

import java.io.IOException;

public class UnicastInputOutputStack
{
    private IUnicastInputLayer.Factory _factInputTop;
    private IUnicastOutputLayer.Factory _factOutputBottom;
    private GlobalLimiter.Factory _factGlobalLimiter;
    private LimitMonitor.Factory _factLimitMonitor;

    private IncomingStreamsThrottler _incomingStreamsThrottler;

    private IUnicastInputLayer _unicastInputBottom;
    private IUnicastOutputLayer _unicastOutputTop;
    private IUnicastInputLayer _unicastInputTop;

    @Inject
    public void inject_(IUnicastOutputLayer.Factory factOutputBottom,
            IUnicastInputLayer.Factory factInputTop,
            GlobalLimiter.Factory factGlobalLimiter, LimitMonitor.Factory factLimitMonitor,
            IncomingStreamsThrottler incomingStreamsThrottler)
    {
        _factInputTop = factInputTop;
        _factOutputBottom = factOutputBottom;
        _factGlobalLimiter = factGlobalLimiter;
        _factLimitMonitor = factLimitMonitor;
        _incomingStreamsThrottler = incomingStreamsThrottler;
    }

    public IUnicastInputLayer inputTop()
    {
        return _unicastInputTop;
    }

    public IUnicastInputLayer input()
    {
        return _unicastInputBottom;
    }

    public IUnicastOutputLayer output()
    {
        return _unicastOutputTop;
    }

    public void init_() throws IOException
    {
        // NOTE: The GlobalLimiter and the LimitMonitor are collectively the ThrottleLayer

        IUnicastInputLayer unicastInputTop = _factInputTop.create_();
        IUnicastOutputLayer unicastOutputBottom = _factOutputBottom.create_();
        GlobalLimiter limiter = _factGlobalLimiter.create_(unicastOutputBottom);
        LimitMonitor limitMonitor = _factLimitMonitor.create_(limiter, unicastInputTop, unicastOutputBottom);

        limitMonitor.init_();

        _unicastInputTop = unicastInputTop;
        _unicastInputBottom = limitMonitor;
        _unicastOutputTop = limiter;

        _incomingStreamsThrottler.setLimitMonitor_(limitMonitor);
    }
}
