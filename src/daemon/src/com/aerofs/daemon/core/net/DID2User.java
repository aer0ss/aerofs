package com.aerofs.daemon.core.net;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.IDID2UserDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.base.id.UserID;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.id.DID;
import com.aerofs.proto.Core.PBCore.Type;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This class records mappings from device IDs to user IDs. They are retrieved either from SP or as
 * port of the security handshake process. Once retrieved, they are permanently stored in the local
 * database.
 */
public class DID2User
{
    private static final Logger l = Loggers.getLogger(DID2User.class);

    private final Map<DID, Set<TCB>> _d2waiters = Maps.newHashMap();
    private final TransportRoutingLayer _trl;
    private final TokenManager _tokenManager;
    private final IDID2UserDatabase _db;
    private final TransManager _tm;

    @Inject
    public DID2User(TokenManager tokenManager, TransportRoutingLayer trl, IDID2UserDatabase db, TransManager tm)
    {
        _tokenManager = tokenManager;
        _trl = trl;
        _db = db;
        _tm = tm;
    }

    /**
     * Retreive the mapping from the local database.
     * @return null if the mapping doesn't exist locally.
     */
    public @Nullable UserID getFromLocalNullable_(DID did)
            throws SQLException
    {
        return _db.getNullable_(did);
    }

    private @Nonnull UserID getFromLocal_(DID did)
            throws SQLException
    {
        UserID user = getFromLocalNullable_(did);
        assert user != null : did;
        return user;
    }

    /**
     * Add the mapping to the local database.
     *
     * @pre the mapping must not exist locally.
     */
    public void addToLocal_(DID did, UserID user, Trans t) throws SQLException
    {
        _db.insert_(did, user, t);
    }

    /**
     * Issues a NOP RPC to trigger TLS handshake with the devices, so the user ID can be retrieved
     * as part of the handshake process. The current thread pauses until the the process
     * succeeds or an error occurs such as timeout. On success, the mapping will be added to the
     * database. Use this method instead of getFromSP if the caller should proceed even if SP is
     * offline.
     *
     * Note that the handshake thread that obtains the mapping should call procesMappingFromPeer_()
     * to wake up the current thread.
     *
     * @pre the mapping must not exist locally.
     */
    public UserID getFromPeer_(DID did) throws Exception
    {
        assert getFromLocalNullable_(did) == null;

        l.debug("resolving user 4 " + did);

        _trl.sendUnicast_(did, CoreUtil.newCall(Type.NOP).build());

        Token tk = _tokenManager.acquireThrows_(Cat.DID2USER, did.toString());
        try {
            Set<TCB> waiters = _d2waiters.get(did);
            if (waiters == null) {
                waiters = new HashSet<TCB>();
                _d2waiters.put(did, waiters);
            }

            TCB tcb = TC.tcb();
            waiters.add(tcb);
            try {
                tk.pause_(Cfg.timeout(), "d2u " + did);
            } finally {
                waiters.remove(tcb);
                if (waiters.isEmpty()) _d2waiters.remove(did);
            }
        } finally {
            tk.reclaim_();
        }

        return getFromLocal_(did);
    }

    /**
     * Call this method only after the user is fully authenticated. Note that this method may start
     * a transaction.
     */
    public void processMappingFromPeer_(DID did, UserID user)
            throws SQLException
    {
        if (getFromLocalNullable_(did) == null) {
            Trans t = _tm.begin_();
            try {
                addToLocal_(did, user, t);
                t.commit_();
            } finally {
                t.end_();
            }
        }

        Set<TCB> waiters = _d2waiters.get(did);
        if (waiters != null) for (TCB tcb : waiters) tcb.resume_();
    }
}
