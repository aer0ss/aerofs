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
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Param.SyncStat;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.syncstat.SyncStatBlockingClient;
import com.aerofs.proto.Syncstat.GetSyncStatusReply;
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
    private final CoreScheduler _sched;
    private final CfgLocalUser _user;
    private final SyncStatBlockingClient.Factory _ssf;
    private ISignInHandler _sih;

    private boolean _firstCall;
    private SyncStatBlockingClient _client;

    public interface ISignInHandler
    {
        /**
         * On sign in, the server sends the client epoch associated with the last successful version
         * hash push. The client should rollback its push epoch if the server epoch indicates data
         * loss.
         */
        void onSignIn_(long clientEpoch) throws Exception;
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
        // Need the while loop because we're releasing the object's monitor while waiting for
        // the sign-in handler to complete (in a core thread)
        while (_client == null) {
            try {
                _client = _ssf.create(SyncStat.URL, _user.get());
                final long epoch = _client.signInRemote();

                /**
                 * Some gymnastic must be done here:
                 * The sign-in handler must be called with the core lock held because it needs to
                 * read from, and possibly write to, the DB. However this method is called with the
                 * core lock released to avoid network-related stalls so we schedule a core event
                 * to call the handler and wait for its completion using wait/notify.
                 */
                final OutArg<Exception> ex = new OutArg<Exception>();
                _sched.schedule(new AbstractEBSelfHandling() {
                    @Override
                    public void handle_()
                    {
                        synchronized (SyncStatusConnection.this) {
                            try {
                                _sih.onSignIn_(epoch);
                            } catch (Exception e) {
                                ex.set(e);
                            }
                            // notify the connection to keep going
                            SyncStatusConnection.this.notify();
                        }
                    }
                }, 0);

                // wait for the sign-in handler to complete
                wait();
                // rethrow any error from the sign-in handler
                if (ex.get() != null) throw ex.get();

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
        ensureConnected_();
        try {
            _client.setVersionHash(sid.toPB(), oids, vhs, clientEpoch);
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
