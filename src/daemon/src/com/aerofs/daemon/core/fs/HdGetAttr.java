package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.event.fs.EIGetAttr;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.Tick;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Ritual.PBBranch.PBPeer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

public class HdGetAttr extends AbstractHdIMC<EIGetAttr>
{
    private final DirectoryService _ds;

    private final ChangeEpochDatabase _cedb;
    private final NativeVersionControl _nvc;
    private final UserAndDeviceNames _udn;
    private final RemoteContentDatabase _rcdb;

    @Inject
    public HdGetAttr(DirectoryService ds, ChangeEpochDatabase cedb, NativeVersionControl nvc,
            RemoteContentDatabase rcdb, UserAndDeviceNames udn)
    {
        _ds = ds;
        _nvc = nvc;
        _udn = udn;
        _cedb = cedb;
        _rcdb = rcdb;
    }

    @Override
    public void handleThrows_(EIGetAttr ev, Prio prio) throws Exception
    {
        // Do not follow anchor
        SOID soid = _ds.resolveNullable_(ev._path);
        if (soid == null) {
            ev.setResult_(null, null);
        } else {
            OA oa = _ds.getOANullable_(soid);
            // oa may be null
            ev.setResult_(oa, oa != null && oa.isFile() ? divergence(soid, oa.cas().keySet()) : null);
        }
    }

    private static class Peer
    {
        private final UserID user;
        private final String device;

        Peer(UserID u, String d)
        {
            user = u;
            device = d;
        }

        @Override
        public int hashCode()
        {
            return user.hashCode();
        }

        @Override
        public boolean equals(Object o)
        {
            if (o == null) return false;
            Peer op = (Peer)o;
            return user.equals(op.user) && (!user.equals(Cfg.user()) || device.equals(op.device));
        }

        @Override
        public String toString()
        {
            if (Cfg.user().equals(user)) return "You on " + device;
            return user.getString();
        }

        PBPeer toPB()
        {
            PBPeer.Builder bd = PBPeer.newBuilder();
            bd.setUserName(user.getString());
            if (user.equals(Cfg.user())) bd.setDeviceName(device);
            return bd.build();
        }
    }

    private static final String UNKNOWN_DEVICE = "(unknown device)";

    /**
     * @return a populated Peer for the device with DID {@paramref did}.
     */
    Peer peer(DID did) throws Exception
    {
        if (did == null) return new Peer(UserID.UNKNOWN, UNKNOWN_DEVICE);
        if (did.equals(Cfg.did())) return new Peer(Cfg.user(), "this computer");
        String device = "";
        UserID user = _udn.getDeviceOwnerNullable_(did);
        if (user == null) {
            user = UserID.UNKNOWN;
        } else if (user.equals(Cfg.user())) {
            device = _udn.getDeviceNameNullable_(did);
            // TODO: fix name resolution... (e.g. do in the background)
            if (device == null) device = UNKNOWN_DEVICE;
        }
        return new Peer(user, device);
    }

    /**
     * @return a map from all branches of an object to the list of contributors for each branch.
     */
    private Map<KIndex, List<PBPeer>> divergence(SOID soid, Set<KIndex> branches) throws Exception
    {
        if (branches.size() < 2) return null;
        Map<KIndex, List<PBPeer>> editors = Maps.newHashMap();

        // TODO(phoenix)
        if (_cedb.getChangeEpoch_(soid.sidx()) != null) {
            checkState(branches.size() == 2);
            // when a conflict branch is present it MUST be the last downloaded version
            // therefore it is still present in the remote content db, from which the
            // originator can be extracted
            editors.put(KIndex.MASTER,
                    ImmutableList.of(peer(Cfg.did()).toPB()));
            editors.put(KIndex.MASTER.increment(),
                    ImmutableList.of(peer(_rcdb.getOriginator_(soid)).toPB()));
            return editors;
        }

        Version vMin = null;
        Map<KIndex, Version> versions = Maps.newHashMap();
        for (KIndex kidx : branches) {
            Version v = _nvc.getLocalVersion_(new SOCKID(soid, CID.CONTENT, kidx));
            versions.put(kidx, v);
            if (vMin == null) {
                vMin = Version.copyOf(v);
            } else {
                vMin = min(vMin, v);
            }
        }

        for (KIndex kidx : branches) {
            List<PBPeer> l = Lists.newArrayList();
            for (Peer p : contributors(versions.get(kidx), vMin)) l.add(p.toPB());
            editors.put(kidx, l);
        }
        return editors;
    }

    // we may consider putting this directly in Version as static method instead
    private Version min(Version u, Version v)
    {
        Version result = Version.empty();

        for (Entry<DID, Tick> entry : u.getAll_().entrySet()) {
            DID did = entry.getKey();
            long uTick = entry.getValue().getLong();
            long vTick = v.get_(did).getLong();

            if (vTick == 0) continue;

            result.set_(did, Math.min(uTick, vTick));
        }

        return result;
    }

    /**
     * @pre ancestor is an ancestor of v (i.e all its ticks are <= to those in v)
     * @return set of peers for which the tick in v is superior to that in ancestor
     * NB: aggregate peers (i.e other users) will be present if at least one of the subpeers
     * (i.e devices) contributes to the version
     */
    private Set<Peer> contributors(Version v, Version ancestor) throws Exception
    {
        Set<Peer> r = Sets.newHashSet();
        for (DID did : v.getAll_().keySet()) {
            if (v.get_(did).getLong() > ancestor.get_(did).getLong()) {
                r.add(peer(did));
            }
        }
        return r;
    }
}
