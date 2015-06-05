package com.aerofs.daemon.transport.ssmp;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.transport.lib.IMulticastListener;
import com.aerofs.daemon.transport.presence.IStoreInterestListener;
import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.SID;
import com.aerofs.ssmp.SSMPClient.ConnectionListener;
import com.aerofs.ssmp.SSMPEvent;
import com.aerofs.ssmp.SSMPEventHandler;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class SSMPPresenceProcessor implements ConnectionListener, SSMPEventHandler {
    private final static Logger l = Loggers.getLogger(SSMPPresenceProcessor.class);

    public final List<IMulticastListener> multicastListeners = new ArrayList<>();
    public final List<IStoreInterestListener> storeInterestListeners = new ArrayList<>();

    private final Multimap<DID, SID> _devices = TreeMultimap.create();

    @Override
    public void eventReceived(SSMPEvent ev) {
        switch (ev.type) {
            case SUBSCRIBE:
                try {
                    DID did = new DID(ev.from.toString());
                    SID sid = new SID(ev.to.toString());
                    updateStores(true, did, sid);
                } catch (ExInvalidID ex) {
                    l.warn("unexpected presence {} {}", ev.from, ev.to);
                }
                break;
            case UNSUBSCRIBE:
                try {
                    DID did = new DID(ev.from.toString());
                    SID sid = new SID(ev.to.toString());
                    updateStores(false, did, sid);
                } catch (ExInvalidID ex) {
                    l.warn("unexpected presence {} {}", ev.from, ev.to);
                }
                break;
            default:
                break;
        }
    }

    private void updateStores(boolean available, DID did, SID sid) {
        boolean deviceTransition, storeTransition;
        l.debug("{} process {} for {}", did, available ? "online" : "offline", sid);

        if (available) {
            deviceTransition = !_devices.containsKey(did);
            storeTransition = _devices.put(did, sid);
        } else {
            // if remove does nothing, the DID:SID map did not exist; bail out early if so
            if (!_devices.remove(did, sid)) {
                return;
            }
            storeTransition = true;
            deviceTransition = !_devices.containsKey(did);
        }

        // handle multicast state transitions:
        if (deviceTransition) {
            l.info("{} recv {} presence for {}", did, available ? "online" : "offline", sid);
            if (available) {
                multicastListeners.forEach(l -> l.onDeviceReachable(did));
            } else {
                multicastListeners.forEach(l -> l.onDeviceUnreachable(did));
            }
        }

        if (storeTransition) {
            if (available) {
                storeInterestListeners.forEach(l -> l.onDeviceJoin(did, sid));
            } else {
                storeInterestListeners.forEach(l -> l.onDeviceLeave(did, sid));
            }
        }
    }

    @Override
    public void connected() {
        multicastListeners.forEach(IMulticastListener::onMulticastReady);
    }

    @Override
    public void disconnected() {
        multicastListeners.forEach(IMulticastListener::onMulticastUnavailable);
    }
}
