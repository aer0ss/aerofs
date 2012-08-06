/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp;

import com.aerofs.daemon.core.net.link.INetworkLinkStateService;
import com.aerofs.daemon.core.net.tng.Preference;
import com.aerofs.daemon.tng.IPeerDiagnoser;
import com.aerofs.daemon.tng.ITransport;
import com.aerofs.daemon.tng.ITransportListener;
import com.aerofs.daemon.tng.ReceivedMaxcastFilter;
import com.aerofs.daemon.tng.base.IEventLoop;
import com.aerofs.daemon.tng.base.IMaxcastService;
import com.aerofs.daemon.tng.base.IPresenceService;
import com.aerofs.daemon.tng.base.pipeline.IPipelineFactory;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.id.DID;

import java.net.InetSocketAddress;
import java.net.Proxy;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

public final class XMPPBasedTransportFactory
{
    private final DID _localdid;
    private final Proxy _proxy;
    private final IEventLoop _eventLoop;
    private final IPeerDiagnoser _peerDiagnoser;
    private final INetworkLinkStateService _networkLinkStateService;
    private final IPresenceService _presenceService;
    private final IMaxcastService _maxcastService;
    private final ISignallingService _signallingService;

    public XMPPBasedTransportFactory(DID localdid, Proxy proxy, IEventLoop eventLoop,
            IPeerDiagnoser peerDiagnoser, INetworkLinkStateService networkLinkStateService,
            ReceivedMaxcastFilter receivedMaxcastFilter)
    {
        this._localdid = localdid;
        this._proxy = proxy;
        this._eventLoop = eventLoop;
        this._peerDiagnoser = peerDiagnoser;
        this._networkLinkStateService = networkLinkStateService;

        XMPPServerConnectionService xmppServerConnectionService = XMPPServerConnectionService.getInstance_(
                _networkLinkStateService, _localdid, _proxy);

        this._presenceService = XMPPPresence.getInstance_(_eventLoop, xmppServerConnectionService,
                _networkLinkStateService);

        XMPPMulticast multicast = XMPPMulticast.getInstance_(_eventLoop,
                xmppServerConnectionService, _networkLinkStateService, receivedMaxcastFilter,
                new FrequentDefectSender(), _localdid);

        this._maxcastService = multicast;
        this._signallingService = multicast;
    }

    public ITransport createJingle_(String id, Preference pref, ITransportListener listener,
            IPipelineFactory pipelineFactory)
    {
        _presenceService.addListener_(listener, sameThreadExecutor());
        _maxcastService.addListener_(listener, sameThreadExecutor());

        return Jingle.getInstance_(_eventLoop, id, pref, _localdid, _proxy, pipelineFactory,
                listener, _peerDiagnoser, _networkLinkStateService, _presenceService,
                _maxcastService);
    }

    public ITransport createZephyr_(String id, Preference pref, InetSocketAddress zephyrAddress,
            ITransportListener listener, IPipelineFactory pipelineFactory)
    {
        _presenceService.addListener_(listener, sameThreadExecutor());
        _maxcastService.addListener_(listener, sameThreadExecutor());

        return Zephyr.getInstance_(_eventLoop, id, pref, _localdid, _proxy, pipelineFactory,
                zephyrAddress, listener, _peerDiagnoser, _networkLinkStateService, _presenceService,
                _maxcastService, _signallingService);
    }
}
