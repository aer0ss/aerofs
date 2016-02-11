package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.ids.DID;
import com.google.common.collect.Iterables;

import java.util.*;
import java.util.stream.Collectors;

public class PresenceLocations {
    private final Map<DID, DeviceLocations> _locations = new HashMap<>();

    public void clear() {
        _locations.clear();
    }

    public synchronized boolean has(DID did) {
        DeviceLocations locs = _locations.get(did);
        return locs != null && locs.candidate() != null;
    }

    public synchronized Set<DID> getAll() {
        return _locations.keySet().stream()
                .filter(this::has)
                .collect(Collectors.toSet());
    }

    public synchronized void remove(DID did) {
        _locations.remove(did);
    }

    public static class DeviceLocations {
        private Set<IPresenceLocation> candidates = new HashSet<>();
        private Set<IPresenceLocation> verified = new HashSet<>();

        synchronized Set<IPresenceLocation> setCandidates(Set<IPresenceLocation> locs) {
            Set<IPresenceLocation> diff = new HashSet<>();
            candidates.retainAll(locs);
            for (IPresenceLocation loc : locs) {
                if (!candidates.contains(loc)) {
                    diff.add(loc);
                }
            }
            candidates.addAll(locs);
            return diff;
        }

        synchronized void removeCandidate(IPresenceLocation loc) {
            candidates.remove(loc);
        }

        synchronized void addVerified(IPresenceLocation loc) {
            verified.add(loc);
        }

        synchronized boolean removeVerified(IPresenceLocation loc) {
            verified.remove(loc);
            return verified.isEmpty();
        }

        public synchronized IPresenceLocation candidate() {
            return Iterables.getFirst(candidates, null);
        }
    }

    public synchronized DeviceLocations get(DID did) {
        DeviceLocations locs = _locations.get(did);
        if (locs == null) {
            locs = new DeviceLocations();
            _locations.put(did, locs);
        }
        return locs;
    }
}
