package com.aerofs.daemon.transport.presence;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.polaris.GsonUtil;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.IMulticastListener;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocationReceiver;
import com.aerofs.ids.DID;
import com.aerofs.ssmp.*;
import com.aerofs.ssmp.SSMPEvent.Type;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.gson.*;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.aerofs.daemon.transport.presence.TCPPresenceLocation.fromExportedLocation;
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
    private final Map<ITransport, List<IPresenceLocation>> _locations = new ConcurrentHashMap<>();

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
        String loc = LOCATIONS + " " + locations();
        if (loc.length() > 1024) {
            l.info("too many locations to fit in SSMP payload: {}", loc);
            return;
        }
        request(SSMPRequest.bcast(loc));
    }

    @Override
    public void eventReceived(SSMPEvent ev) {
        String d = new String(ev.payload, StandardCharsets.UTF_8).substring(LOCATIONS.length() + 1);
        if (ev.type == Type.BCAST) {
            parseLocations(ev.from.toString(), d);
        } else if (ev.type == Type.UCAST) {
            if (d.equals(REQUEST)) {
                String loc = LOCATIONS + " " + locations();
                l.info("{} {}", ev.from, loc);
                if (loc.length() > 1024) {
                    l.info("too many locations to fit in SSMP payload");
                    return;
                }
                request(SSMPRequest.ucast(ev.from, loc));
            } else {
                parseLocations(ev.from.toString(), d);
            }
        }
    }

    private void parseLocations(String from, String s) {
        try {
            DID did = new DID(from);
            JsonObject transports = new JsonParser().parse(s).getAsJsonObject();
            for (Entry<String, JsonElement> e : transports.entrySet()) {
                if (!e.getKey().equals("t")) {
                    l.debug("unsupported transport location: {} {}", e.getKey(), e.getValue());
                    continue;
                }
                JsonArray locations = e.getValue().getAsJsonArray();
                for (JsonElement addr : locations) {
                    try {
                        IPresenceLocation loc = fromExportedLocation(did, addr.getAsString());
                        l.info("location {} {}", did, addr);
                        _listeners.forEach(listener -> listener.onPresenceReceived(loc));
                    } catch (ExInvalidPresenceLocation ex) {
                        l.warn("invalid location: {}", ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            l.warn("invalid locations", BaseLogUtil.suppress(e, ClosedChannelException.class));
        }
    }

    private String locations() {
        return _locations.entrySet().stream()
                .map(e -> GsonUtil.GSON.toJson(e.getKey().id()) + ":" + e.getValue().stream()
                                .map(l -> GsonUtil.GSON.toJson(l.exportLocation()))
                                .collect(Collectors.joining(",", "[", "]"))
                ).collect(Collectors.joining(",", "{", "}"));
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
                l.info("request failed {}", r, BaseLogUtil.suppress(t, ClosedChannelException.class));
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
