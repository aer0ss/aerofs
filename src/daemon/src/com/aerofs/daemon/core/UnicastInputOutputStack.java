/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core;

import com.aerofs.daemon.core.net.CoreProtocolReactor;
import com.aerofs.daemon.core.net.IUnicastInputLayer;
import com.aerofs.daemon.core.net.IUnicastOutputLayer;
import com.aerofs.daemon.core.net.UnicastOutputBottomLayer;
import com.aerofs.daemon.core.net.throttling.GlobalLimiter;
import com.aerofs.daemon.core.net.throttling.IncomingStreamsThrottler;
import com.aerofs.daemon.core.net.throttling.LimitMonitor;
import com.google.inject.Inject;

import java.io.IOException;

public class UnicastInputOutputStack
{
    private CoreProtocolReactor.Factory _factInputTop;
    private UnicastOutputBottomLayer.Factory _factOutputBottom;
    private GlobalLimiter.Factory _factGlobalLimiter;
    private LimitMonitor.Factory _factLimitMonitor;

    private IncomingStreamsThrottler _incomingStreamsThrottler;

    private IUnicastInputLayer _unicastInputBottom;
    private IUnicastOutputLayer _unicastOutputTop;
    private CoreProtocolReactor _unicastInputTop;

    @Inject
    public void inject_(UnicastOutputBottomLayer.Factory factOutputBottom,
            CoreProtocolReactor.Factory factInputTop,
            GlobalLimiter.Factory factGlobalLimiter, LimitMonitor.Factory factLimitMonitor,
            IncomingStreamsThrottler incomingStreamsThrottler)
    {
        _factInputTop = factInputTop;
        _factOutputBottom = factOutputBottom;
        _factGlobalLimiter = factGlobalLimiter;
        _factLimitMonitor = factLimitMonitor;
        _incomingStreamsThrottler = incomingStreamsThrottler;
    }

    public CoreProtocolReactor inputTop()
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

        CoreProtocolReactor unicastInputTop = _factInputTop.create_();
        UnicastOutputBottomLayer unicastOutputBottom = _factOutputBottom.create_();
        GlobalLimiter limiter = _factGlobalLimiter.create_(unicastOutputBottom);
        LimitMonitor limitMonitor = _factLimitMonitor.create_(limiter, unicastInputTop, unicastOutputBottom);

        limitMonitor.init_();

        _unicastInputTop = unicastInputTop;
        _unicastInputBottom = limitMonitor;
        _unicastOutputTop = limiter;

        _incomingStreamsThrottler.setLimitMonitor_(limitMonitor);
    }
}
