package com.aerofs.daemon.transport.presence;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.IMulticastListener;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocationReceiver;
import com.aerofs.ids.DID;
import com.aerofs.ssmp.*;
import com.aerofs.ssmp.SSMPEvent.Type;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

/**
 * LocationManager is in charge of advertising presence locations to remote peers over the SSMP
 * connection and dispatching corresponding advertisements from remote peers to listeners.
 *
 * BCAST / UCAST routing prefix: "loc"
 *
 * BCAST locations update
 * UCAST locations request / response
 */
public class LocationManager implements EventHandler, IMulticastListener {
    private final static Logger l = Loggers.getLogger(LocationManager.class);

    private final SSMPConnection _c;
    private final Map<ITransport, List<IPresenceLocation>> _locations = new HashMap<>();

    private final String LOCATIONS = "loc";
    private final String REQUEST = "req";

    private final List<IPresenceLocationReceiver> _listeners = new ArrayList<>();

    @Inject
    public LocationManager(SSMPConnection c) {
        _c = c;
        c.addUcastHandler(LOCATIONS, this);
        c.addBcastHandler(LOCATIONS, this);
    }

    public void addPresenceLocationListener(IPresenceLocationReceiver l) {
        _listeners.add(l);
    }

    public void onLocationChanged(ITransport tp, List<IPresenceLocation> locations) {
        _locations.put(tp, locations);
        request(SSMPRequest.bcast(LOCATIONS + " " + locations()));
    }

    @Override
    public void eventReceived(SSMPEvent ev) {
        String d = ev.payload.substring(LOCATIONS.length() + 1);
        if (ev.type == Type.BCAST) {
            parseLocations(ev.from.toString(), d);
        } else if (ev.type == Type.UCAST) {
            if (d.equals(REQUEST)) {
                l.info("locations request {} {}", ev.from, locations());
                request(SSMPRequest.ucast(ev.from, LOCATIONS + " " + locations()));
            } else {
                parseLocations(ev.from.toString(), d);
            }
        }
    }

    private void parseLocations(String from, String s) {
        try {
            DID did = new DID(from);
            JsonArray locations = new JsonParser().parse(s).getAsJsonArray();
            for (JsonElement e : locations) {
                try {
                    IPresenceLocation loc = PresenceLocationFactory.fromJson(did, e.getAsJsonObject());
                    l.info("location {} {}", did, loc);
                    _listeners.forEach(listener -> listener.onPresenceReceived(loc));
                } catch (ExInvalidPresenceLocation ex) {
                    l.warn("invalid location: {}", ex.getMessage());
                }
            }
        } catch (Exception e) {
            l.warn("invalid locations", e);
        }
    }

    private String locations() {
        return _locations.values().stream()
                .map(ll -> ll.stream()
                                .map(l -> l.toJson().toString())
                                .collect(Collectors.joining(","))
        ).collect(Collectors.joining(",", "[", "]"));
    }

    private void requestLocations(DID did) {
        request(SSMPRequest.ucast(SSMPIdentifier.fromInternal(did.toStringFormal()),
                LOCATIONS + " " + REQUEST));
    }

    private void request(SSMPRequest r) {
        Futures.addCallback(_c.request(r), new FutureCallback<SSMPResponse>() {
            @Override
            public void onSuccess(SSMPResponse result) {
                if (result.code != SSMPResponse.OK) {
                    l.info("request failed {} {}", r, result.code);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                l.info("request failed {}", r, t);
            }
        }, sameThreadExecutor());
    }

    @Override
    public void onMulticastReady() {}

    @Override
    public void onMulticastUnavailable() {}

    @Override
    public void onDeviceReachable(DID did) {
        requestLocations(did);
    }

    @Override
    public void onDeviceUnreachable(DID did) {}
}
