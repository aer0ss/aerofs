package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.C;
import com.aerofs.daemon.core.net.TransportFactory;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ISignallingService;
import com.aerofs.daemon.transport.lib.IMulticastListener;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocationReceiver;
import com.aerofs.daemon.transport.presence.IStoreInterestListener;
import com.aerofs.daemon.transport.xmpp.multicast.XMPPMulticast;
import com.aerofs.daemon.transport.xmpp.presence.XMPPPresenceProcessor;
import com.aerofs.daemon.transport.xmpp.signalling.SignallingService;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import org.jivesoftware.smack.SASLAuthentication;

import java.util.ArrayList;
import java.util.List;

/**
 * Factor out all XMPP setup/teardown
 */
public class Xmpp implements IMulticastListener, IStoreInterestListener, IPresenceLocationReceiver {
    private final XMPPParams params;

    public final XMPPConnectionService xmpp;
    private final XMPPPresenceProcessor presence;
    public final XMPPMulticast multicast;;

    public final List<IMulticastListener> multicastListeners = new ArrayList<>();
    public final List<IStoreInterestListener> storeInterestListeners = new ArrayList<>();
    public final List<IPresenceLocationReceiver> presenceLocationListeners = new ArrayList<>();

    public Xmpp(XMPPParams params, CfgLocalDID localdid, IBlockingPrioritizedEventSink<IEvent> sink,
                MaxcastFilterReceiver maxcastFilterReceiver,
                LinkStateService linkStateService) {
        this.params = params;
        // this is required to avoid a NullPointerException during authentication
        // see http://www.igniterealtime.org/community/thread/35976
        SASLAuthentication.supportSASLMechanism("PLAIN", 0);

        xmpp = new XMPPConnectionService(
                localdid.get(),
                params.serverAddress,
                params.serverDomain,
                TransportFactory.TransportType.ZEPHYR.toString(),
                5 * C.SEC,
                3,
                LibParam.EXP_RETRY_MIN_DEFAULT,
                LibParam.EXP_RETRY_MAX_DEFAULT,
                linkStateService
        );

        multicast = new XMPPMulticast(localdid.get(), params.serverDomain,
                maxcastFilterReceiver, xmpp, sink);

        presence = new XMPPPresenceProcessor(localdid.get(), params.serverDomain,
                this, this, this);

        // WARNING: it is very important that XMPPPresenceProcessor listen to XMPPConnectionService
        // _before_ XMPPMulticast. The reason is that XMPPMulticast will join the chat rooms and this will trigger
        // sending the presence information. So if we add XMPPMulticast as a listener first, the presence
        // information will already be sent by the time the presence manager registers to get them.
        xmpp.addListener(presence);
        xmpp.addListener(multicast);
    }

    public ISignallingService newSignallingService(String transportId) {
        SignallingService s = new SignallingService(transportId, params.serverDomain);
        xmpp.addListener(s);
        return s;
    }

    @Override
    public void onPresenceReceived(IPresenceLocation presenceLocation) {
        presenceLocationListeners.forEach(l -> l.onPresenceReceived(presenceLocation));
    }

    @Override
    public void onMulticastReady() {
        multicastListeners.forEach(IMulticastListener::onMulticastReady);
    }

    @Override
    public void onMulticastUnavailable() {
        multicastListeners.forEach(IMulticastListener::onMulticastUnavailable);
    }

    @Override
    public void onDeviceReachable(DID did) {
        multicastListeners.forEach(l -> l.onDeviceReachable(did));
    }

    @Override
    public void onDeviceUnreachable(DID did) {
        multicastListeners.forEach(l -> l.onDeviceUnreachable(did));
    }

    @Override
    public void onDeviceJoin(DID did, SID sid) {
        storeInterestListeners.forEach(l -> l.onDeviceJoin(did, sid));
    }

    @Override
    public void onDeviceLeave(DID did, SID sid) {
        storeInterestListeners.forEach(l -> l.onDeviceLeave(did, sid));
    }
}
