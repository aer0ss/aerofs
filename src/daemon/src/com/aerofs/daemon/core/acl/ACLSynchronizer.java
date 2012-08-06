package com.aerofs.daemon.core.acl;

import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.IACLDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Param.SP;
import com.aerofs.lib.Role;
import com.aerofs.lib.SubjectRolePair;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.spsv.SPBlockingClient;
import com.aerofs.lib.spsv.SPClientFactory;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sp.GetACLReply;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.collect.Maps.newHashMapWithExpectedSize;

/**
 * ACL: Access Control List. AeroFS uses Discretionary ACL.
 *
 * This class is responsible for communicating with the central ACL database and the push service
 * to synchronize with the local ACL database (which is implemented by LocalACL)
 */
public class ACLSynchronizer
{
    private static final Logger l = Util.l(ACLSynchronizer.class);

    private final TC _tc;
    private final TransManager _tm;
    private final IACLDatabase _adb;
    private final LocalACL _lacl;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;

    private class ServerACLReturn
    {
        final long  _serverEpoch;
        final Map<SID, Map<String, Role>> _acl;

        private ServerACLReturn(long serverEpoch, Map<SID, Map<String, Role>> acl)
        {
            _serverEpoch = serverEpoch;
            _acl = acl;
        }
    }

    @Inject
    public ACLSynchronizer(TC tc, TransManager tm, IACLDatabase adb, LocalACL lacl,
            IMapSIndex2SID sIndex2SID, IMapSID2SIndex sid2SIndex)
    {
        _tc = tc;
        _tm = tm;
        _adb = adb;
        _lacl = lacl;
        _sidx2sid = sIndex2SID;
        _sid2sidx = sid2SIndex;
    }

    /**
     * Fetch ACL from the server and save to the local ACL. This method may block to communicate
     * with SP.
     * @throws ExConcurrentACLUpdate if multiple requests to syncToLocal_() happen at the same time
     * (which is possible since the method yield the core lock)
     */
    public void syncToLocal_()
            throws Exception
    {
        long localEpochBeforeSPCall = _adb.getEpoch_();
        updateACLFromSP_(localEpochBeforeSPCall);
    }

    /**
     * Fetch ACL from the server and save to the local ACL. This method may block to communicate
     * with SP.
     * @throws ExConcurrentACLUpdate if multiple requests to syncToLocal_() happen at the same time
     * (which is possible since the method yield the core lock)
     */
    public void syncToLocal_(long serverEpoch)
            throws Exception
    {
        long localEpochBeforeSPCall = _adb.getEpoch_();
        if (serverEpoch == localEpochBeforeSPCall) {
            l.info("acl notification ignored server epoch:" + serverEpoch + " local epoch:" +
                    localEpochBeforeSPCall);
            return;
        }
        updateACLFromSP_(localEpochBeforeSPCall);
    }

    private void updateACLFromSP_(long localEpochBeforeSPCall)
            throws Exception
    {
        ServerACLReturn serverACLReturn = getServerACL_(localEpochBeforeSPCall);

        //
        // get the local acl again. we do this to verify that another acl update didn't sneak in
        // while we were parked
        //
        // hmm...I swear with the exponential retries we're going to have some bouncing here...
        // maybe it's better to always make the call to sp
        //
        // IMPORTANT: I can do this check out here (instead of inside the transaction) because I'm
        // now holding the core lock and no one else can operate on the db
        //

        long localEpochAfterSPCall = _adb.getEpoch_();
        if (localEpochAfterSPCall != localEpochBeforeSPCall) {
            throw new ExConcurrentACLUpdate(localEpochBeforeSPCall, localEpochAfterSPCall);
        }

        // check if our acls match and don't update the local system if they do
        if (serverACLReturn._serverEpoch == localEpochAfterSPCall) {
            l.info("server has no acl updates");
            return;
        }

        Trans t = _tm.begin_();
        try {
            _lacl.clear_(t);

            for (Map.Entry<SID, Map<String, Role>> entry : serverACLReturn._acl.entrySet()) {

                // gets the sidx; an sidx is created if it doesn't exist

                SID sid = entry.getKey();
                SIndex sidx = _sid2sidx.getNullable_(sid);
                if (sidx == null) {
                    sidx = _sid2sidx.getAbsent_(sid, t);
                }

                // add entries for this store to the db

                _lacl.set_(sidx, entry.getValue(), t); // invalidates the cache
            }

            _adb.setEpoch_(serverACLReturn._serverEpoch, t);

            t.commit_();
        } finally {
            t.end_();
        }
    }

    private ServerACLReturn getServerACL_(long localEpoch)
            throws Exception
    {
        GetACLReply aclReply;
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "spacl");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("spacl");
            SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
            sp.signInRemote();
            aclReply = sp.getACL(localEpoch);
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
        }

        long serverEpoch = aclReply.getEpoch();
        l.info("server return acl with epoch:" + serverEpoch);

        if (aclReply.getStoreAclCount() == 0) {
            return new ServerACLReturn(serverEpoch, Collections.<SID, Map<String, Role>>emptyMap());
        }

        Map<SID, Map<String, Role>> acl = newHashMapWithExpectedSize(aclReply.getStoreAclCount());
        for (PBStoreACL storeACL : aclReply.getStoreAclList()) {
            SID sid = new SID(storeACL.getStoreId());

            // if the acl map doesn't already contain an entry for this sid, then add an empty
            // hash map for this
            // FWIW, it's longer to use "newHashMapWithExpectedSize" here than to simply call new

            if (!acl.containsKey(sid)) {
                acl.put(sid, new HashMap<String, Role>(storeACL.getSubjectRoleCount()));
            }

            Map<String, Role> subjectToRoleMap = acl.get(sid);
            for (PBSubjectRolePair pbPair : storeACL.getSubjectRoleList()) {
                subjectToRoleMap.put(pbPair.getSubject(), Role.fromPB(pbPair.getRole()));
            }
        }

        return new ServerACLReturn(serverEpoch, acl);
    }

    public void set_(SIndex sidx, Map<String, Role> subject2role)
            throws Exception
    {
        //
        // call SP first to avoid setting local ACL if SP returns no permissions or other errors.
        //

        // first, resolve the sid
        SID sid = _sidx2sid.getNullable_(sidx);
        if (sid == null) sid = _sidx2sid.getAbsent_(sidx);

        ArrayList<PBSubjectRolePair> roles = new ArrayList<PBSubjectRolePair>();
        for (Map.Entry<String, Role> pair : subject2role.entrySet()) {
            roles.add(new SubjectRolePair(pair.getKey(), pair.getValue()).toPB());
        }

        // make the SP call
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "spacl");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("spacl");
            SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
            sp.signInRemote();
            sp.setACL(sid.toPB(), roles);
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
        }

        //
        // for faster UI refresh and syncing with newly added users, immediately add the entries to
        // the local database rather than waiting for notifications from the push service.
        //

        Trans t = _tm.begin_();
        try {
            _lacl.set_(sidx, subject2role, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }

    public void delete_(SIndex sidx, Iterable<String> subjects)
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
            SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
            sp.signInRemote();
            sp.deleteACL(sid.toPB(), subjects);
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
            _lacl.delete_(sidx, subjects, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
