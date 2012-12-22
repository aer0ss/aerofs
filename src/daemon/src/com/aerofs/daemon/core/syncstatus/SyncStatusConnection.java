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
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.base.id.SID;
import com.aerofs.syncstat.client.SyncStatusBlockingClient;
import com.aerofs.proto.SyncStatus.GetSyncStatusReply;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;

import java.util.List;

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
    private final SyncStatusBlockingClient.Factory _ssf;

    // fields protected by synchronized (this)
    private boolean _firstCall;
    private SyncStatusBlockingClient _client;

    /**
     * Sign-in must be handled by the caller in a thread holding the core lock as it needs to access
     * the database. In addition rollbacks need to interrupt any ongoing maintainance activity to
     * prevent the rolledback epochs from being overridden by ongoing maintenance.
     *
     * A typed exception is used as it allows the easiest and safest interruption of the maintenance
     * task making the call and simplifies interaction with the core lock.
     */
    static class ExSignIn extends Exception
    {
        private static final long serialVersionUID = 0L;

        public final long _epoch;

        ExSignIn(long epoch)
        {
            _epoch = epoch;
        }
    }

    @Inject
    SyncStatusConnection(CfgLocalUser user, TC tc, SyncStatusBlockingClient.Factory ssf)
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
     * and the core lock released
     */
    private void ensureConnected_() throws Exception
    {
        if (_client != null) return;

        long epoch;
        try {
            SyncStatusBlockingClient client = _ssf.create(SyncStat.URL, _user.get());
            epoch = client.signInRemote();
            _firstCall = true;
            _client = client;
        } catch (Exception e) {
            _client = null;
            throw e;
        }

        // ensure immediate handling of sign-in and perform any necessary rollback before
        // making any RPC calls
        throw new ExSignIn(epoch);
    }

    /**
     * Emit connection notification after first successful call
     *
     * This is needed because the syncstat server will sometimes accept incoming connections but
     * fail to service subsequent calls. The connection would remain in a CONNECTED state for a
     * whole minute (default socket timeout) while waiting for a call to complete, revert to
     * DISCONNECTED at the end of that minute and quickly jump back to CONNECTED as the connection
     * would again be accepted upon retry (but still not serviced)... As a result, sync status would
     * mistakenly be assumed to be up-to-date even though the connection wouldn't make any progress.
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
    public void setVersionHash_(SID sid, List<ByteString> oids, List<ByteString> vhs,
            long clientEpoch) throws Exception
    {
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "syncstatpush");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("syncstatpush");
            setVersionHash(sid, oids, vhs, clientEpoch);
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
    public synchronized void setVersionHash(SID sid, List<ByteString> oids, List<ByteString> vhs,
            long clientEpoch) throws Exception
    {
        int attempts = 0;

        // Use a while loop to avoid code dup and to allow for a single retry when we our session is
        // expired.
        while (true) {
            attempts++;
            ensureConnected_();

            try {
                _client.setVersionHash(sid.toPB(), oids, vhs, clientEpoch);
                notifyOnFirstSuccessfulCall_();
                return;
            } catch (ExNoPerm e) {
                reset_();

                if (attempts > 1) {
                    throw e;
                }
            } catch (Exception e) {
                reset_();
                throw e;
            }
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
        int attempts = 0;

        // Use a while loop to avoid code dup and to allow for a single retry when we our session is
        // expired.
        while (true) {
            attempts++;
            ensureConnected_();

            try {
                GetSyncStatusReply r = _client.getSyncStatus(ssEpoch);
                notifyOnFirstSuccessfulCall_();
                return r;
            } catch (ExNoPerm e) {
                reset_();

                if (attempts > 1) {
                    throw e;
                }
            } catch (Exception e) {
                reset_();
                throw e;
            }
        }
    }
}
