package com.aerofs.daemon.core.acl;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.IACLDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.labeling.L;
import com.aerofs.lib.FileUtil;
import com.aerofs.base.acl.Role;
import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.lib.id.SIndex;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Sp.GetSharedFolderNamesReply;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;

import java.io.File;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    private final TC _tc;
    private final TransManager _tm;
    private final IACLDatabase _adb;
    private final LocalACL _lacl;
    private final IStoreJoiner _storeJoiner;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;
    private final CfgLocalUser _cfgLocalUser;
    private final SPBlockingClient.Factory _factSP;

    private class ServerACLReturn
    {
        final long  _serverEpoch;
        final Map<SID, Map<UserID, Role>> _acl;
        final Map<SID, String> _newStoreNames;

        private ServerACLReturn(long serverEpoch, Map<SID, Map<UserID, Role>> acl,
                Map<SID, String> newStoreNames)
        {
            _serverEpoch = serverEpoch;
            _acl = acl;
            _newStoreNames = newStoreNames;
        }
    }

    @Inject
    public ACLSynchronizer(TC tc, TransManager tm, IACLDatabase adb, LocalACL lacl,
            IStoreJoiner storeJoiner, IMapSIndex2SID sIndex2SID, IMapSID2SIndex sid2SIndex,
            CfgLocalUser cfgLocalUser, SPBlockingClient.Factory factSP)
    {
        _tc = tc;
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

        /**
         * Ugly hack: syncdet test accounts are not currently cleaned and accumulate a huge number
         * of shared folder over times. The auto-join workflow causes all shared folder to be
         * recreated on every new install. For end-users this is unlikely to be a problem as we now
         * automatically leave shared folders when they are deleted. For system tests however where
         * there is no assurance that the daemon will be in a sane enough state to leave all new
         * shared folders at the end of the run this becomes a problem very quickly. Leaving folders
         * *before* running tests would work but it would require serialization of tests to avoid
         * leaving shared folders used by a syncdet test running in parallel..
         *
         * TODO: sanitize SP db around syncdet runs and remove this nasty hack
         */
        File noJoinFlagFile = new File(Cfg.absRTRoot(), "nojoin");
        boolean noAutoJoin = noJoinFlagFile.exists() && !L.get().isMultiuser();

        Trans t = _tm.begin_();
        try {
            updateACLAndAutoJoinLeaveStores_(serverACLReturn, noAutoJoin, t);
            t.commit_();
        } finally {
            t.end_();
        }

        // delete flag file to allow further share/join operations to work as expected
        if (noAutoJoin) FileUtil.delete(noJoinFlagFile);
    }

    private void updateACLAndAutoJoinLeaveStores_(ServerACLReturn serverACLReturn,
            boolean noAutoJoin, Trans t) throws Exception
    {
        Set<SIndex> stores = _lacl.getAccessibleStores_();

        l.debug("accessible stores: {}", stores);

        _lacl.clear_(t);

        for (Map.Entry<SID, Map<UserID, Role>> entry : serverACLReturn._acl.entrySet()) {
            SID sid = entry.getKey();
            Map<UserID, Role> roles = entry.getValue();

            // the local user should always be present in the ACL for each store in the reply
            if (!roles.containsKey(_cfgLocalUser.get())) {
                l.error("Invalid ACL update " + roles);
                throw new ExProtocolError("Invalid ACL update " + roles);
            }

            // create a new SIndex if needed
            SIndex sidx = getOrCreateSIndex_(sid, t);

            if (!stores.contains(sidx) && !noAutoJoin) {
                // not known and accessible: auto-join
                assert serverACLReturn._newStoreNames.containsKey(sid) : sid;
                String folderName = serverACLReturn._newStoreNames.get(sid);
                _storeJoiner.joinStore_(sidx, sid, folderName, t);
            }
            stores.remove(sidx);

            // invalidates the cache
            _lacl.set_(sidx, roles, t);
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

    private ServerACLReturn getServerACL_(long localEpoch)
            throws Exception
    {
        GetACLReply aclReply;
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "spacl");
        TCB tcb = null;
        SPBlockingClient sp;
        try {
            tcb = tk.pseudoPause_("spacl");
            sp = _factSP.create_(_cfgLocalUser.get());
            sp.signInRemote();
            aclReply = sp.getACL(localEpoch);
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
        }

        long serverEpoch = aclReply.getEpoch();
        l.info("server return acl with epoch:" + serverEpoch);

        if (aclReply.getStoreAclCount() == 0) {
            return new ServerACLReturn(serverEpoch, Collections.<SID, Map<UserID, Role>>emptyMap(),
                    Collections.<SID, String>emptyMap());
        }

        // keep track of new stores: we need to query their names from SP
        List<ByteString> newStores = Lists.newArrayList();
        Map<SID, Map<UserID, Role>> acl = newHashMapWithExpectedSize(aclReply.getStoreAclCount());
        for (PBStoreACL storeACL : aclReply.getStoreAclList()) {
            SID sid = new SID(storeACL.getStoreId());

            // if the acl map doesn't already contain an entry for this sid, then add an empty
            // hash map for this
            // FWIW, it's longer to use "newHashMapWithExpectedSize" here than to simply call new

            if (!acl.containsKey(sid)) {
                acl.put(sid, new HashMap<UserID, Role>(storeACL.getSubjectRoleCount()));
                if (isNewStore_(sid)) {
                    l.debug("new store {}", sid);
                    newStores.add(sid.toPB());
                }
            }

            Map<UserID, Role> subjectToRoleMap = acl.get(sid);
            for (PBSubjectRolePair pbPair : storeACL.getSubjectRoleList()) {
                SubjectRolePair srp = new SubjectRolePair(pbPair);
                subjectToRoleMap.put(srp._subject, srp._role);
            }
        }

        return new ServerACLReturn(serverEpoch, acl, getStoreNames_(sp, newStores));
    }

    private boolean isNewStore_(SID sid) throws SQLException
    {
        SIndex sidx = _sid2sidx.getLocalOrAbsentNullable_(sid);
        return (sidx == null || _lacl.get_(sidx).get(_cfgLocalUser.get()) == null);
    }

    private Map<SID, String> getStoreNames_(SPBlockingClient sp, List<ByteString> storeIds)
            throws Exception
    {
        Map<SID, String> storeNames = Maps.newHashMap();
        if (!storeIds.isEmpty()) {
            Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "spfoldername");
            TCB tcb = null;
            try {
                tcb = tk.pseudoPause_("spfoldername");
                GetSharedFolderNamesReply reply = sp.getSharedFolderNames(storeIds);
                assert storeIds.size() == reply.getFolderNameCount();
                for (int i = 0; i < storeIds.size(); ++i) {
                    storeNames.put(new SID(storeIds.get(i)), reply.getFolderName(i));
                }
            } finally {
                if (tcb != null) tcb.pseudoResumed_();
                tk.reclaim_();
            }
        }
        return storeNames;
    }

    /**
     * Update ACLs via the SP.updateACL call
     */
    public void update_(SIndex sidx, UserID subject, Role role)
            throws Exception
    {
        // first, resolve the sid and prepare data for protobuf
        SID sid = resolveSIndex_(sidx);

        // make the SP call (done before adding entries to the local database to avoid changing the
        // local database if the SP call fails)
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "spacl");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("spacl");
            SPBlockingClient sp = _factSP.create_(_cfgLocalUser.get());
            sp.signInRemote();
            sp.updateACL(sid.toPB(), subject.getString(), role.toPB());
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
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "spacl");
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
