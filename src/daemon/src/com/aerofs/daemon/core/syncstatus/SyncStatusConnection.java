/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.syncstatus;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.serverstatus.AbstractConnectionStatusNotifier;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.lib.Param.SyncStat;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.syncstat.SyncStatBlockingClient;
import com.aerofs.proto.Syncstat.GetSyncStatusReply;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

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
    private static final Logger l = Util.l(SyncStatusConnection.class);

    private final TC _tc;
    private final CoreScheduler _sched;
    private final CfgLocalUser _user;
    private final SyncStatBlockingClient.Factory _ssf;
    private ISignInHandler _sih;

    // fields protected by synchronized (this)
    private boolean _firstCall;
    private SyncStatBlockingClient _client;

    public interface ISignInHandler
    {
        /**
         * On sign in, the server sends the client epoch associated with the last successful version
         * hash push. The client should rollback its push epoch if the server epoch indicates data
         * loss.
         */
        void onSignIn_(long clientEpoch);
    }

    @Inject
    SyncStatusConnection(CfgLocalUser user, TC tc, CoreScheduler sched,
            SyncStatBlockingClient.Factory ssf)
    {
        _tc = tc;
        _ssf = ssf;
        _user = user;
        _sched = sched;
        _client = null;
    }

    void setSignInHandler(ISignInHandler sih)
    {
        _sih = sih;
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

        try {
            SyncStatBlockingClient client = _ssf.create(SyncStat.URL, _user.get());
            final long epoch = client.signInRemote();

            l.debug("sss signed-in");

            /**
             * Schedule sign-in handler
             * This method may modify the db if the last known client epoch returned by the sign-in
             * call is lower than the push epoch (in which case a rollback is needed to recover from
             * server-side data loss). It must therefore be called with the core lock held. Because
             * the likelihood of such a rollback is very low, and because the rollback will abort
             * any conflicting maintenance operation (e.g ongoing activity log scan), we do not need
             * to wait for the event handler to complete.
             */
            _sched.schedule(new AbstractEBSelfHandling() {
                @Override
                public void handle_()
                {
                    _sih.onSignIn_(epoch);
                }
            }, 0);

            _firstCall = true;
            _client = client;
        } catch (Exception e) {
            _client = null;
            throw e;
        }
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
