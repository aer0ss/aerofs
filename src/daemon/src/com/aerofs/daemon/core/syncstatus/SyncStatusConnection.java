/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.syncstatus;

import com.aerofs.daemon.core.serverstatus.AbstractConnectionStatusNotifier;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.Param.SyncStat;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.syncstat.SyncStatBlockingClient;
import com.aerofs.proto.Syncstat.GetSyncStatusReply;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;

/**
 * Single persistent connection to sync status server
 *
 * The purpose of this class is to ensure each client only has one connection to the sync status
 * server, that the status of this connection is available through the IConnectionStatusNotifier
 * interface and that access to the underlying RPC client is properly synchronized.
 *
 * Another benefit is that it abstracts away reconnection handling and the sign-in step.
 */
public class SyncStatusConnection extends AbstractConnectionStatusNotifier
{
    private final TC _tc;
    private final CfgLocalUser _user;
    private final SyncStatBlockingClient.Factory _ssf;

    private boolean _firstCall;
    private SyncStatBlockingClient _client;

    @Inject
    SyncStatusConnection(CfgLocalUser user, TC tc, SyncStatBlockingClient.Factory ssf)
    {
        _tc = tc;
        _ssf = ssf;
        _user = user;
        _client = null;
    }

    /**
     * Try connecting if needed and notify listeners in case of successful connection
     *
     * NOTE: should only be called with the object lock held (i.e synchronized method or similar)
     */
    private void ensureConnected_() throws Exception
    {
        if (_client == null) {
            try {
                _client = _ssf.create(SyncStat.URL, _user.get());
                _client.signInRemote();
                _firstCall = true;
            } catch (Exception e) {
                _client = null;
                throw e;
            }
        }
    }

    /**
     * Emit connection notification after first successful call
     *
     * This is needed because the syncstat server will sometimes accept imcoming connections but
     * fail to service subsequent calls which will cause the connection to quickly jump back to
     * a CONNECTED state after a disconnection even though it isn't making any actual progress.
     *
     * NOTE: should only be called with the object lock held (i.e synchronized method or similar)
     */
    private void notifyOnFirstSuccessfulCall_()
    {
        if (_firstCall) {
            notifyConnected_();
            _firstCall = false;
        }
    }

    /**
     * Discard the current client (to enforce reconnect on next RPC) and notify listeners of
     * the disconnection.
     *
     * NOTE: should only be called with the object lock held (i.e synchronized method or similar)
     */
    private void reset_()
    {
        _client = null;
        notifyDisconnected_();
    }

    /**
     * Releases the core lock around the setVersionHash RPC call
     */
    public void setVersionHash_(OID oid, SID sid, byte[] vh) throws Exception
    {
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "syncstatpush");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("syncstatpush");
            setVersionHash(oid, sid, vh);
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
        }
    }

    /**
     * Synchronized wrapper of setVersionHash RPC call
     *
     * NOTE: should not be called with the core lock held
     */
    public synchronized void setVersionHash(OID oid, SID sid, byte[] vh) throws Exception
    {
        ensureConnected_();
        try {
            _client.setVersionHash(oid.toPB(), sid.toPB(), ByteString.copyFrom(vh));
            notifyOnFirstSuccessfulCall_();
        } catch (Exception e) {
            reset_();
            throw e;
        }
    }

    /**
     * Releases the core lock around the getSyncStatus RPC call
     */
    public GetSyncStatusReply getSyncStatus_(long ssEpoch) throws Exception
    {
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "syncstatpull");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("syncstatpull");
            return getSyncStatus(ssEpoch);
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
        }
    }

    /**
     * Synchronized wrapper of getSyncStatus RPC call
     *
     * NOTE: should not be called with the core lock held
     */
    public synchronized GetSyncStatusReply getSyncStatus(long ssEpoch) throws Exception
    {
        ensureConnected_();
        try {
            GetSyncStatusReply r = _client.getSyncStatus(ssEpoch);
            notifyOnFirstSuccessfulCall_();
            return r;
        } catch (Exception e) {
            reset_();
            throw e;
        }
    }
}
