/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.jingle;

import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.base.IUnicastConnection;
import com.aerofs.daemon.tng.base.WireData;
import com.aerofs.lib.Util;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.ex.ExJingle;
import com.aerofs.base.id.DID;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.apache.log4j.Logger;

import java.net.NetworkInterface;

final class JingleUnicastConnection implements IUnicastConnection
{
    private static final Logger l = Util.l(JingleUnicastConnection.class);

    private final ISingleThreadedPrioritizedExecutor _executor;
    private final DID _remoteDID;
    private final SignalThread _signalThread;

    private final SettableFuture<Void> _connectFuture;
    private final UncancellableFuture<Void> _disconnectFuture;

    public JingleUnicastConnection(ISingleThreadedPrioritizedExecutor executor, DID remoteDID,
            SignalThread signalThread)
    {
        _executor = executor;
        _remoteDID = remoteDID;
        _signalThread = signalThread;

        _connectFuture = SettableFuture.create();
        _disconnectFuture = UncancellableFuture.create();
    }

    public void onNetworkLinksRemoved_(ImmutableSet<NetworkInterface> removed)
    {

    }

    @Override
    public ListenableFuture<Void> getCloseFuture_()
    {
        // FIXME: placeholder!
        return null;
    }

    @Override
    public ListenableFuture<Void> connect_()
    {
        // Make sure we're not running on the signaling thread, or
        // this method will block
        _signalThread.assertNotThread();

        // This does not need to be called in our executor, as we're essentially
        // enqueue-ing on a different thread here anyway
        _signalThread.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                Engine eng = _signalThread.getEngine_();
                if (eng != null && !eng.isClosed_()) {
                    eng.connect_(_remoteDID);
                } else {
                    error(new ExJingle("j: engine closed for connect"));
                }
            }

            @Override
            public void error(Exception e)
            {
                l.warn("j: fail connect for d:" + _remoteDID + " err:" + e);
                _connectFuture.setException(e);
            }
        });

        return _connectFuture;
    }

    @Override
    public ListenableFuture<Void> send_(final byte[][] bss, final Prio pri)
    {
        // Make sure we're not running on the signalling thread, or
        // this method will block
        _signalThread.assertNotThread();

        final UncancellableFuture<Void> future = UncancellableFuture.create();

        // This does not need to be called in our executor, as we're essentially
        // enqueuing on a different thread here anyway
        _signalThread.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                Engine eng = _signalThread.getEngine_();
                if (eng != null && !eng.isClosed_()) {
                    eng.send_(_remoteDID, bss, pri, future);
                } else {
                    error(new ExJingle("engine closed"));
                }
            }

            @Override
            public void error(Exception e)
            {
                future.setException(e);
            }

        });

        return future;
    }

    @Override
    public ListenableFuture<Void> disconnect_(final Exception ex)
    {
        assert _connectFuture.isDone();
        assert !_connectFuture.isCancelled();

        // Make sure we're not running on the signalling thread, or
        // this method will block
        _signalThread.assertNotThread();

        // This does not need to be called in our executor, as we're essentially
        // enqueueing on a different thread here anyway
        _signalThread.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                _signalThread.close_(_remoteDID, ex);
            }

            @Override
            public void error(Exception e)
            { /* silently ignore */ }
        });
        return _disconnectFuture;
    }

    @Override
    public ListenableFuture<ImmutableList<WireData>> receive_()
    {
        final UncancellableFuture<ImmutableList<WireData>> future = UncancellableFuture.create();
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {

            }

        });
        return future;
    }

    /**
     * Called from JingleUnicastConnectionService when a Jingle channel is connected
     */
    public void onJingleChannelConnected()
    {
        // Set the connect future
        _connectFuture.set(null);
    }

    /**
     * Called from JingleUnicastConnectionService when a Jingle channel is disconnected, or failed
     * to connect
     */
    public void onJingleChannelFailed()
    {
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                if (_connectFuture.isDone()) {
                    // The connection failed after a successful connection, so
                    // notify set the disconnection future
                    _disconnectFuture.set(null);
                } else {
                    _connectFuture.setException(new Exception("Jingle connection failed"));
                }
            }
        });
    }

    /**
     * Called from JingleUnicastConnectionService when a Jingle channel has received data
     */
    /*
    public void onJingleChannelReceivedData(final byte[] data, final int wirelen)
    {
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                assert _connectFuture.isDone() && !_connectFuture.isCancelled();

                _notifier.notifyOnOtherThreads(new OnIncomingBytes(JingleUnicastConnection.this, data, wirelen));
            }
        });

    }
    */
}
