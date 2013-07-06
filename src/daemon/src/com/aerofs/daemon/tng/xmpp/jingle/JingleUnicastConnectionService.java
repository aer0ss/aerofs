/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.jingle;

import com.aerofs.base.BaseParam.Xmpp;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.base.IIncomingUnicastConnectionListener;
import com.aerofs.daemon.tng.base.INetworkStats;
import com.aerofs.daemon.tng.base.IUnicastConnection;
import com.aerofs.daemon.tng.base.IUnicastConnectionService;
import com.aerofs.daemon.tng.xmpp.ID;
import com.aerofs.daemon.tng.xmpp.ISignallingClient;
import com.aerofs.daemon.tng.xmpp.ISignallingService.SignallingMessage;
import com.aerofs.j.Jid;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.lib.ex.ExJingle;
import com.aerofs.lib.notifier.SingleListenerNotifier;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Files.PBDumpStat;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;

import java.io.PrintStream;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public final class JingleUnicastConnectionService
        implements IUnicastConnectionService, ISignallingClient, IJingle
{
    private static final Logger l = Loggers.getLogger(JingleUnicastConnectionService.class);

    private final ISingleThreadedPrioritizedExecutor _executor;
    private final INetworkStats _networkStats;
    private final SingleListenerNotifier<IIncomingUnicastConnectionListener> _notifier = SingleListenerNotifier
            .create();

    private final SignalThread _signalThread;

    private final Map<DID, JingleUnicastConnection> _connections = new HashMap<DID, JingleUnicastConnection>();

    public JingleUnicastConnectionService(ISingleThreadedPrioritizedExecutor executor,
            INetworkStats networkStats)
    {
        OSUtil.get().loadLibrary("aerofsj");

        _executor = executor;
        _networkStats = networkStats;

        _signalThread = new SignalThread(this);
        _signalThread.setDaemon(true);
        _signalThread.setName("j");
    }

    @Override
    public void setListener_(IIncomingUnicastConnectionListener listener,
            Executor notificationExecutor)
    {
        _notifier.setListener(listener, notificationExecutor);
    }

    @Override
    public void start_()
    {
        _signalThread.start();
    }

    @Override
    public IUnicastConnection createConnection_(final DID did)
    {
        final JingleUnicastConnection connection = new JingleUnicastConnection(_executor, did,
                _signalThread);
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                _connections.put(did, connection);
            }
        });

        return connection;
    }

    @Override
    public void onLinkStateChanged_(ImmutableSet<NetworkInterface> previous,
            ImmutableSet<NetworkInterface> current, ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed)
    {
        // FIXME: TODO!
    }

    @Override
    public void signallingChannelConnected_()
    {
        connectionStateChanged(true);
    }

    @Override
    public void signallingChannelDisconnected_()
    {
        connectionStateChanged(false);
    }

    private void connectionStateChanged(boolean up)
    {
        reconnect();

        _signalThread.linkStateChanged(up);
    }

    private void reconnect()
    {
        l.info("request jingle to reconnect");

        _signalThread.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                // the signal thread will retry connection after close_() is
                // called
                _signalThread.close_(new ExJingle("reconnection requested"));
            }

            @Override
            public void error(Exception e)
            { /* silently ignore */ }
        });
    }

    @Override
    public void processSignallingMessage_(SignallingMessage message)
    {
        assert false : ("Did not register to receive any messages");
    }

    @Override
    public void addBytesRx(long bytesrx)
    {
        _signalThread.assertThread();

        _networkStats.addBytesRx(bytesrx);
    }

    @Override
    public void addBytesTx(long bytestx)
    {
        _signalThread.assertThread();

        _networkStats.addBytesTx(bytestx);
    }

    @Override
    public void peerConnected(final DID did)
    {
        _signalThread.assertThread();

        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                JingleUnicastConnection connection = _connections.get(did);
                assert connection != null;

                // This can be called directly since both JingleUnicastConnectionService
                // and JingleUnicastConnection share the same executor
                connection.onJingleChannelConnected();
            }
        });
    }

    @Override
    public void peerDisconnected(final DID did)
    {
        _signalThread.assertThread();

        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                JingleUnicastConnection connection = _connections.get(did);
                assert connection != null;

                // This can be called directly since both JingleUnicastConnectionService
                // and JingleUnicastConnection share the same executor
                connection.onJingleChannelFailed();
            }
        });
    }

    @Override
    public void incomingConnection(final DID did, final ISignalThreadTask acceptTask)
    {
        _signalThread.assertThread();

        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                // This will getInstance_ the connection and add it our internal map
                JingleUnicastConnection connection = new JingleUnicastConnection(_executor, did,
                        _signalThread);
                _connections.put(did, connection);

                // Notify the listener of the new incoming connection
                ListenableFuture<Void> future = _notifier.notifyOnOtherThreads(
                        new IIncomingUnicastConnectionListener.Visitor(did, connection));
                future.addListener(new Runnable()
                {

                    @Override
                    public void run()
                    {
                        // The listener has executed, so now run the acceptTask
                        _signalThread.call(acceptTask);
                    }

                }, _executor);
            }
        });
    }

    @Override
    public void processData(final DID did, final byte[] data, final int wirelen)
    {
        _signalThread.assertThread();

        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                JingleUnicastConnection connection = _connections.get(did);
                assert connection != null;

                // This can be called directly since both JingleUnicastConnectionService
                // and JingleUnicastConnection share the same executor
                // FIXME: connection.onJingleChannelReceivedData(data, wirelen);
            }
        });
    }

    @Override
    public void closePeerStreams(DID did, boolean outbound, boolean inbound)
    {
        _signalThread.assertThread();

        // TODO: Not sure what to do here
        assert false;
    }

    // TODO: Replace with proper id
    private String id()
    {
        return "Jingle";
    }

    @Override
    public void dumpStat(PBDumpStat template, final PBDumpStat.Builder bd)
            throws Exception
    {
        final PBDumpStat.PBTransport tp = template.getTransport(0);
        assert tp != null : ("called dumpstat on transport with null tp");

        // set default fields
        final PBDumpStat.PBTransport.Builder tpbuilder = PBDumpStat.PBTransport.newBuilder();
        tpbuilder.setBytesIn(_networkStats.getBytesRx());
        tpbuilder.setBytesOut(_networkStats.getBytesTx());

        // set a default for the diagnosis
        if (tp.hasDiagnosis()) {
            tpbuilder.setDiagnosis("call not executed");
        }

        _signalThread.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                if (tp.hasName()) {
                    tpbuilder.setName(id());
                }

                if (tp.getConnectionCount() != 0) {
                    for (DID c : _signalThread.getConnections_()) {
                        tpbuilder.addConnection(c.toString());
                    }
                }

                if (tp.hasDiagnosis()) {
                    tpbuilder.setDiagnosis(_signalThread.diagnose_());
                }

                bd.addTransport(tpbuilder);
            }

            @Override
            public void error(Exception e)
            {
                l.warn("cannot dumpstat err:" +
                        e); // hmm...using JingleUnicastConnectionService's logger, not SignalThread's
            }
        });
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        // ideally we want to do an _st.call, but in this _particular_ case
        // we don't have to because all it's doing is checking if a reference
        // is null

        _signalThread.dumpStatMisc(indent, indentUnit, ps);
    }

    /**
     * Convert a <code>DID</code> to an XMPPBasedTransportFactory <code>JID</code> valid on the
     * AeroFS XMPPBasedTransportFactory server
     *
     * @param did {@link DID} to convert
     * @return a valid XMPPBasedTransportFactory user id of the form: {$user}@{$domain}/{$resource}
     */
    static Jid did2jid(DID did)
    {
        return new Jid(ID.did2user(did), Xmpp.SERVER_DOMAIN.get(), ID.resource(true));
    }

    static DID jid2did(Jid jid)
            throws ExFormatError
    {
        return ID.user2did(jid.node());
    }

}
