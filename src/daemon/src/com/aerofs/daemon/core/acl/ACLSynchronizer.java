package com.aerofs.daemon.core.acl;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Role;
import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.IACLDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
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
    private final IStoreJoiner _storeJoiner;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;
    private final CfgLocalUser _cfgLocalUser;
    private final SPBlockingClient.Factory _factSP;

    private static class StoreInfo
    {
        public final String _name;
        public final boolean _external;
        public final ImmutableMap<UserID, Role> _roles;

        // See docs/design/sharing_and_migration.txt for information about the external flag.
        StoreInfo(String name, boolean external, ImmutableMap<UserID, Role> roles)
        {
            _name = name;
            _external = external;
            _roles = roles;
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
            IStoreJoiner storeJoiner, IMapSIndex2SID sIndex2SID, IMapSID2SIndex sid2SIndex,
            CfgLocalUser cfgLocalUser, SPBlockingClient.Factory factSP)
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

    private void commitToLocal_(SIndex sidx, UserID userID, Role role)
            throws SQLException, ExNotFound
    {
        Trans t = _tm.begin_();
        try {
            _lacl.set_(sidx, Collections.singletonMap(userID, role), t);
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

        l.debug("accessible stores: {}", stores);

        _lacl.clear_(t);

        for (Map.Entry<SID, StoreInfo> entry : serverACLReturn._acl.entrySet()) {
            SID sid = entry.getKey();
            StoreInfo storeInfo = entry.getValue();
            Map<UserID, Role> roles = storeInfo._roles;

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

            if (!stores.contains(sidx) && (_sidx2sid.getNullable_(sidx) == null)) {
                // not known and accessible: auto-join
                assert storeInfo._name != null : sid;
                _storeJoiner.joinStore_(sidx, sid, storeInfo._name, storeInfo._external, t);
            }
            stores.remove(sidx);
        }

        // leave stores to which we no longer have access
        for (SIndex sidx : stores) _storeJoiner.leaveStore_(sidx, getSID_(sidx), t);

        _adb.setEpoch_(serverACLReturn._serverEpoch, t);
    }

    private SID getSID_(SIndex sidx) throws SQLException
    {
        SID sid = _sidx2sid.getNullable_(sidx);
        return sid != null ? sid : _sidx2sid.getAbsent_(sidx);
    }

    private SIndex getOrCreateSIndex_(SID sid, Trans t) throws Exception
    {
        SIndex sidx = _sid2sidx.getNullable_(sid);
        return sidx != null ? sidx : _sid2sidx.getAbsent_(sid, t);
    }

    /**
     * Note: this code is very similar to com.aerofs.android.service.ACLManager#refreshACL()
     */
    private ServerACLReturn getServerACL_(long localEpoch)
            throws Exception
    {
        GetACLReply aclReply;
        SPBlockingClient sp;

        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "spacl");
        try {
            TCB tcb = tk.pseudoPause_("spacl");
            try {
                sp = _factSP.create_(_cfgLocalUser.get());
                sp.signInRemote();
                aclReply = sp.getACL(localEpoch);
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
            ImmutableMap.Builder<UserID, Role> builder = ImmutableMap.builder();
            for (PBSubjectRolePair pbPair : store.getSubjectRoleList()) {
                SubjectRolePair srp = new SubjectRolePair(pbPair);
                builder.put(srp._subject, srp._role);
            }

            StoreInfo si = new StoreInfo(store.getName(), store.getExternal(), builder.build());
            stores.put(new SID(store.getStoreId()), si);
        }

        return new ServerACLReturn(serverEpoch, stores);
    }

    /**
     * Update ACLs via the SP.updateACL call
     */
    public void update_(SIndex sidx, UserID subject, Role role,
            boolean suppressSharedFolderRulesWarnings)
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
            SPBlockingClient sp = _factSP.create_(_cfgLocalUser.get());
            sp.signInRemote();
            sp.updateACL(sid.toPB(), subject.getString(), role.toPB(),
                    suppressSharedFolderRulesWarnings);
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
        }

        // add new entries to the local database
        commitToLocal_(sidx, subject, role);
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
            SPBlockingClient sp = _factSP.create_(_cfgLocalUser.get());
            sp.signInRemote();
            sp.deleteACL(sid.toPB(), subject.getString());
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
