package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.event.fs.EIGetAttr;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Ritual.PBBranch.PBPeer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

public class HdGetAttr extends AbstractHdIMC<EIGetAttr>
{
    private final DirectoryService _ds;

    private final UserAndDeviceNames _udn;
    private final CentralVersionDatabase _cvdb;
    private final RemoteContentDatabase _rcdb;

    @Inject
    public HdGetAttr(DirectoryService ds, CentralVersionDatabase cvdb, RemoteContentDatabase rcdb,
                     UserAndDeviceNames udn)
    {
        _ds = ds;
        _udn = udn;
        _cvdb = cvdb;
        _rcdb = rcdb;
    }

    @Override
    public void handleThrows_(EIGetAttr ev) throws Exception
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
        if (user == null && _udn.updateLocalDeviceInfo_(ImmutableList.of(did))) {
            user = _udn.getDeviceOwnerNullable_(did);
        }
        if (user == null) {
            user = UserID.UNKNOWN;
        } else if (user.equals(Cfg.user())) {
            device = _udn.getDeviceNameNullable_(did);
            if (device == null && _udn.updateLocalDeviceInfo_(ImmutableList.of(did))) {
                device = _udn.getDeviceNameNullable_(did);
            }
            if (device == null) device = UNKNOWN_DEVICE;
        }
        return new Peer(user, device);
    }

    /**
     * @return a map from all branches of an object to the list of contributors for each branch.
     */
    private Map<KIndex, PBPeer> divergence(SOID soid, Set<KIndex> branches) throws Exception
    {
        if (branches.size() < 2) return null;
        Map<KIndex, PBPeer> editors = Maps.newHashMap();

        checkState(branches.size() == 2);
        editors.put(KIndex.MASTER, peer(Cfg.did()).toPB());

        // see DaemonConflictHandler/CentralVersionDatabase
        // dummy conflict branch has null version, does not match any rcdb entry
        Long v = _cvdb.getVersion_(soid.sidx(), soid.oid());
        if (v == null) {
            editors.put(KIndex.MASTER.increment(), PBPeer.newBuilder().setUserName("").build());
        } else {
            // when a conflict branch is present it MUST be the last downloaded version
            // therefore it is still present in the remote content db, from which the
            // originator can be extracted
            // TODO: check consistency (first rcdb entry should match cvdb)
            editors.put(KIndex.MASTER.increment(), peer(_rcdb.getOriginator_(soid)).toPB());
        }
        return editors;
    }
}
