package com.aerofs.daemon.transport.ssmp;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.transport.lib.IMulticastListener;
import com.aerofs.daemon.transport.presence.IStoreInterestListener;
import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.SID;
import com.aerofs.ssmp.SSMPClient.ConnectionListener;
import com.aerofs.ssmp.SSMPEvent;
import com.aerofs.ssmp.EventHandler;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class SSMPPresenceProcessor implements ConnectionListener, EventHandler {
    private final static Logger l = Loggers.getLogger(SSMPPresenceProcessor.class);

    public final List<IMulticastListener> multicastListeners = new ArrayList<>();
    public final List<IStoreInterestListener> storeInterestListeners = new ArrayList<>();

    private final Multimap<DID, SID> _devices = TreeMultimap.create();

    @Override
    public void eventReceived(SSMPEvent ev) {
        try {
            DID did = new DID(ev.from.toString());
            SID sid = new SID(ev.to.toString());
            switch (ev.type) {
                case SUBSCRIBE:   updateStores(true, did, sid);  break;
                case UNSUBSCRIBE: updateStores(false, did, sid); break;
                default: break;
            }
        } catch (ExInvalidID ex) {
            l.warn("unexpected presence {} {}", ev.from, ev.to);
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
        // cleanup device map and notify listeners appropriately
        // TODO: more efficient cleanup (requires all listeners to do their own efficient cleanup)
        for (Entry<DID, SID> e : ImmutableList.copyOf(_devices.entries())) {
            updateStores(false, e.getKey(), e.getValue());
        }
        multicastListeners.forEach(IMulticastListener::onMulticastUnavailable);
    }
}
