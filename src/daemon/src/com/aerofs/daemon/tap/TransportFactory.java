/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap;

import com.aerofs.daemon.core.net.link.ILinkStateService;
import com.aerofs.daemon.core.net.tng.Preference;
import com.aerofs.daemon.tng.ITransport;
import com.aerofs.daemon.tng.ITransportListener;
import com.aerofs.daemon.tng.ReceivedMaxcastFilter;
import com.aerofs.daemon.tng.base.EventQueueBasedEventLoop;
import com.aerofs.daemon.tng.base.pipeline.IPipelineFactory;
import com.aerofs.daemon.tng.diagnosis.PeerDiagnoser;
import com.aerofs.daemon.tng.xmpp.XMPPBasedTransportFactory;
import com.aerofs.lib.id.DID;
import com.aerofs.proto.Tap.StartTransportCall;
import com.google.inject.BindingAnnotation;
import com.google.inject.Inject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.InetSocketAddress;
import java.net.Proxy;

public class TransportFactory
{
    @Inject private EventQueueBasedEventLoop _eventLoop;
    @Inject private ReceivedMaxcastFilter _maxcastFilter;
    @Inject private PeerDiagnoser _peerDiagnoser;
    @Inject private ILinkStateService _networkLinkStateService;
    @Inject private Proxy _proxy;
    @Inject @LocalDID private DID _localDID;
    @Inject @ZephyrAddress private InetSocketAddress _zephyrAddress;

    public ITransport create(StartTransportCall.Type type, ITransportListener listener,
            IPipelineFactory pipelineFactory)
    {
        if (type == StartTransportCall.Type.ZEPHYR || type == StartTransportCall.Type.JINGLE) {
            XMPPBasedTransportFactory xmppTransportsFactory = new XMPPBasedTransportFactory(
                    _localDID, _proxy, _eventLoop, _peerDiagnoser, _networkLinkStateService,
                    _maxcastFilter);

            if (type == StartTransportCall.Type.ZEPHYR) {
                return xmppTransportsFactory.createZephyr_("zephyr", new Preference(0),
                        _zephyrAddress, listener, pipelineFactory);
            }
        }

        return null;
    }

    public ILinkStateService getLinkStateService()
    {
        return _networkLinkStateService;
    }

    @BindingAnnotation
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ZephyrAddress
    {
    }

    @BindingAnnotation
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface LocalDID
    {
    }
}
