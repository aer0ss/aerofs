package com.aerofs.daemon.core;

import com.aerofs.daemon.core.net.*;
import com.aerofs.daemon.core.net.dtls.DTLSLayer;
import com.aerofs.daemon.core.net.throttling.GlobalLimiter;
import com.aerofs.daemon.core.net.throttling.IncomingStreamsThrottler;
import com.aerofs.daemon.core.net.throttling.LimitMonitor;
import com.google.inject.Inject;

import java.io.IOException;

public class UnicastInputOutputStack
{
    private UnicastInputTopLayer.Factory _factInputTop;
    private UnicastOutputBottomLayer.Factory _factOutputBottom;
    private GlobalLimiter.Factory _factGlobalLimiter;
    private LimitMonitor.Factory _factLimitMonitor;
    private DTLSLayer.Factory _factDTLS;

    private IncomingStreamsThrottler _incomingStreamsThrottler;

    private IUnicastInputLayer _unicastInputBottom;
    private IUnicastOutputLayer _unicastOutputTop;
    private UnicastInputTopLayer _unicastInputTop;

    @Inject
    public void inject_(UnicastOutputBottomLayer.Factory factOutputBottom,
            UnicastInputTopLayer.Factory factInputTop, DTLSLayer.Factory factDTLS,
            GlobalLimiter.Factory factGlobalLimiter, LimitMonitor.Factory factLimitMonitor,
            IncomingStreamsThrottler incomingStreamsThrottler)
    {
        _factInputTop = factInputTop;
        _factOutputBottom = factOutputBottom;
        _factGlobalLimiter = factGlobalLimiter;
        _factLimitMonitor = factLimitMonitor;
        _factDTLS = factDTLS;
        _incomingStreamsThrottler = incomingStreamsThrottler;
    }

    public UnicastInputTopLayer inputTop()
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

        DTLSLayer dtls = _factDTLS.create_();
        UnicastInputTopLayer unicastInputTop = _factInputTop.create_();
        UnicastOutputBottomLayer unicastOutputBottom = _factOutputBottom.create_();
        GlobalLimiter limiter = _factGlobalLimiter.create_(unicastOutputBottom);
        LimitMonitor limitMonitor = _factLimitMonitor.create_(limiter, dtls, unicastOutputBottom);

        limitMonitor.init_();
        dtls.init_(unicastInputTop, limiter);

        _unicastInputTop = unicastInputTop;
        _unicastInputBottom = limitMonitor;
        _unicastOutputTop = dtls;

        _incomingStreamsThrottler.setLimitMonitor_(limitMonitor);
    }
}
