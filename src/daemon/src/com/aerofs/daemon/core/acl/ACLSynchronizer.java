package com.aerofs.daemon.core.acl;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.SubjectPermissions;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.store.AbstractStoreJoiner;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.IACLDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.labeling.L;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.google.common.collect.Maps.newHashMapWithExpectedSize;

/**
 * ACL: Access Control List. AeroFS uses Discretionary ACL.
 *
 * This class is responsible for communicating with the central ACL database and the push service
 * to synchronize with the local ACL database (which is implemented by LocalACL)
 */
public class ACLSynchronizer
{
    private static final Logger l = Loggers.getLogger(ACLSynchronizer.class);

    private final TokenManager _tokenManager;
    private final TransManager _tm;
    private final IACLDatabase _adb;
    private final LocalACL _lacl;
    private final AbstractStoreJoiner _storeJoiner;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;
    private final CfgLocalUser _cfgLocalUser;
    private final SPBlockingClient.Factory _factSP;

    private static class StoreInfo
    {
        public final String _name;
        public final boolean _external;
        public final ImmutableMap<UserID, Permissions> _roles;
        public final Set<UserID> _externalMembers;

        // See docs/design/sharing_and_migration.txt for information about the external flag.
        StoreInfo(String name, boolean external, ImmutableMap<UserID, Permissions> roles,
                Set<UserID> externalMembers)
        {
            _name = name;
            _external = external;
            _roles = roles;
            _externalMembers = externalMembers;
        }
    }

    private class ServerACLReturn
    {
        final long  _serverEpoch;
        final Map<SID, StoreInfo> _acl;

        private ServerACLReturn(long serverEpoch, Map<SID, StoreInfo> acl)
        {
            _serverEpoch = serverEpoch;
            _acl = acl;
        }
    }

    @Inject
    public ACLSynchronizer(TokenManager tokenManager, TransManager tm, IACLDatabase adb, LocalACL lacl,
            AbstractStoreJoiner storeJoiner, IMapSIndex2SID sIndex2SID, IMapSID2SIndex sid2SIndex,
            CfgLocalUser cfgLocalUser, InjectableSPBlockingClientFactory factSP)
    {
        _tokenManager = tokenManager;
        _tm = tm;
        _adb = adb;
        _lacl = lacl;
        _storeJoiner = storeJoiner;
        _sidx2sid = sIndex2SID;
        _sid2sidx = sid2SIndex;
        _cfgLocalUser = cfgLocalUser;
        _factSP = factSP;
    }

    private void commitToLocal_(SIndex sidx, UserID userID, Permissions permissions)
            throws SQLException, ExNotFound
    {
        Trans t = _tm.begin_();
        try {
            _lacl.set_(sidx, Collections.singletonMap(userID, permissions), t);
            t.commit_();
        } finally {
            t.end_();
        }
    }

    /**
     * Resolve the given SIndex to a SID
     * TODO: move this to a common location so other classes can use it too
     */
    private SID resolveSIndex_(SIndex sidx)
            throws SQLException
    {
        SID sid = _sidx2sid.getNullable_(sidx);
        if (sid == null) sid = _sidx2sid.getAbsent_(sidx);

        return sid;
    }

    /**
     * Fetch ACL from the server and save to the local ACL. This method may block to communicate
     * with SP.
     */
    public void syncToLocal_() throws Exception
    {
        long localEpochBeforeSPCall = _adb.getEpoch_();
        updateACLFromSP_(localEpochBeforeSPCall);
    }

    /**
     * Fetch ACL from the server and save to the local ACL. This method may block to communicate
     * with SP.
     */
    public void syncToLocal_(long serverEpoch) throws Exception
    {
        long localEpochBeforeSPCall = _adb.getEpoch_();
        if (serverEpoch == localEpochBeforeSPCall) {
            l.info("acl notification ignored server epoch:" + serverEpoch + " local epoch:" +
                    localEpochBeforeSPCall);
            return;
        }
        updateACLFromSP_(localEpochBeforeSPCall);
    }

    /**
     * The most important role of this function is to ensure that ACL update never go backward as
     * it may cause inconsistent ACL state across clients and more importantly could result in data
     * loss if a shared folder is accidentally kicked out.
     *
     * Because ACL updates are *not incremental* we need to check that whatever epoch corresponds to
     * the result of the SP call is superior to the local epoch *after* the core lock is retaken
     * (RPC is done with core lock released).
     */
    private void updateACLFromSP_(long localEpochBeforeSPCall) throws Exception
    {
        ServerACLReturn serverACLReturn = getServerACL_(localEpochBeforeSPCall);

        //
        // get the local acl again. we do this to verify that another acl update didn't sneak in
        // while we were parked
        //
        // IMPORTANT: I can do this check out here (instead of inside the transaction) because I'm
        // now holding the core lock and no one else can operate on the db
        //

        long localEpochAfterSPCall = _adb.getEpoch_();
        if (serverACLReturn._serverEpoch <= localEpochAfterSPCall) {
            l.info("server has no acl updates " + localEpochBeforeSPCall + " " +
                    localEpochAfterSPCall + " " + serverACLReturn._serverEpoch);
            return;
        }

        Trans t = _tm.begin_();
        try {
            updateACLAndAutoJoinLeaveStores_(serverACLReturn, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }

    private void updateACLAndAutoJoinLeaveStores_(ServerACLReturn serverACLReturn,
            Trans t) throws Exception
    {
        Set<SIndex> stores = _lacl.getAccessibleStores_();
        Map<SIndex, Set<UserID>> newMembers = Maps.newHashMap();

        l.debug("accessible stores: {}", stores);

        _lacl.clear_(t);

        for (Map.Entry<SID, StoreInfo> entry : serverACLReturn._acl.entrySet()) {
            SID sid = entry.getKey();
            StoreInfo storeInfo = entry.getValue();
            Map<UserID, Permissions> roles = storeInfo._roles;

            l.debug("processing ACL: {} {} {} {}", sid, storeInfo._external, storeInfo._name, roles);

            // the local user should always be present in the ACL for each store in the reply
            if (!roles.containsKey(_cfgLocalUser.get())) {
                String msg = "Invalid ACL update " + roles;
                l.error(msg);
                throw new ExProtocolError(msg);
            }

            // create a new SIndex if needed
            SIndex sidx = getOrCreateSIndex_(sid, t);

            // invalidates the cache
            // NB: needs to be done *BEFORE* auto-join
            _lacl.set_(sidx, roles, t);

            // TODO: handle changes of the external bit
            if (!stores.contains(sidx) && (_sidx2sid.getNullable_(sidx) == null)) {
                // not known and accessible: auto-join
                assert storeInfo._name != null : sid;
                _storeJoiner.joinStore_(sidx, sid, storeInfo._name, storeInfo._external, t);
            }
            stores.remove(sidx);

            // list of members that ought to have an anchor on TS
            if (!sid.isUserRoot() && L.isMultiuser()) {
                newMembers.put(sidx, Sets.filter(
                        Sets.difference(roles.keySet(), storeInfo._externalMembers),
                        new Predicate<UserID>() {
                            @Override
                            public boolean apply(UserID user)
                            {
                                return !user.isTeamServerID();
                            }
                        }));
            }
        }

        // leave stores to which we no longer have access
        for (SIndex sidx : stores) {
            _storeJoiner.leaveStore_(sidx, _sidx2sid.getLocalOrAbsent_(sidx), t);
        }

        // for TS, must be done AFTER auto-join/auto-leave
        for (Entry<SIndex, Set<UserID>> e : newMembers.entrySet()) {
            SIndex sidx = e.getKey();
            SID sid = _sidx2sid.getNullable_(sidx);
            if (sid == null) continue;
            _storeJoiner.adjustAnchors_(sidx, serverACLReturn._acl.get(sid)._name, e.getValue(), t);
        }

        _adb.setEpoch_(serverACLReturn._serverEpoch, t);
    }

    private SIndex getOrCreateSIndex_(SID sid, Trans t) throws Exception
    {
        SIndex sidx = _sid2sidx.getNullable_(sid);
        return sidx != null ? sidx : _sid2sidx.getAbsent_(sid, t);
    }

    private ServerACLReturn getServerACL_(long localEpoch)
            throws Exception
    {
        GetACLReply aclReply;

        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "spacl");
        try {
            TCB tcb = tk.pseudoPause_("spacl");
            try {
                aclReply = _factSP.create()
                        .signInRemote()
                        .getACL(localEpoch);
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }

        long serverEpoch = aclReply.getEpoch();
        Map<SID, StoreInfo> stores = newHashMapWithExpectedSize(aclReply.getStoreAclCount());
        l.info("server return acl server epoch {} local epoch {}", serverEpoch, localEpoch);

        for (PBStoreACL store : aclReply.getStoreAclList()) {
            Set<UserID> externalMembers = Sets.newHashSet();
            ImmutableMap.Builder<UserID, Permissions> builder = ImmutableMap.builder();
            for (PBSubjectPermissions pbPair : store.getSubjectPermissionsList()) {
                SubjectPermissions srp = new SubjectPermissions(pbPair);
                builder.put(srp._subject, srp._permissions);
                if (pbPair.hasExternal() && pbPair.getExternal()) {
                    externalMembers.add(srp._subject);
                }
            }

            StoreInfo si = new StoreInfo(store.getName(), store.getExternal(), builder.build(),
                    externalMembers);
            stores.put(new SID(store.getStoreId()), si);
        }

        return new ServerACLReturn(serverEpoch, stores);
    }

    /**
     * Update ACLs via the SP.updateACL call
     */
    public void update_(SIndex sidx, UserID subject, Permissions permissions,
            boolean suppressSharingRulesWarnings)
            throws Exception
    {
        // first, resolve the sid and prepare data for protobuf
        SID sid = resolveSIndex_(sidx);

        // make the SP call (done before adding entries to the local database to avoid changing the
        // local database if the SP call fails)
        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "spacl");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("spacl");
            _factSP.create()
                    .signInRemote()
                    .updateACL(sid.toPB(), subject.getString(), permissions.toPB(),
                            suppressSharingRulesWarnings);
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
        }

        // add new entries to the local database
        commitToLocal_(sidx, subject, permissions);
    }

    public void delete_(SIndex sidx, UserID subject)
            throws Exception
    {
        //
        // call SP first to avoid setting local ACL if SP returns no permission or other errors.
        //

        SID sid = _sidx2sid.get_(sidx); // first, resolve the sid

        // make the SP call
        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "spacl");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("spacl");
            _factSP.create()
                    .signInRemote()
                    .deleteACL(sid.toPB(), subject.getString());
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
        }

        //
        // for faster UI refresh and banning the removed users, immediately add the entries to
        // the local database rather than waiting for notifications from the push service.
        //

        Trans t = _tm.begin_();
        try {
            _lacl.delete_(sidx, subject, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
