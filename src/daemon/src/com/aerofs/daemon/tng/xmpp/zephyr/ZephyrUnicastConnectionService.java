/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr;

import com.aerofs.base.id.DID;
import com.aerofs.base.net.ZephyrConstants;
import com.aerofs.daemon.core.net.link.ILinkStateService;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tng.base.IIncomingUnicastConnectionListener;
import com.aerofs.daemon.tng.base.IIncomingUnicastConnectionListener.Visitor;
import com.aerofs.daemon.tng.base.INetworkStats;
import com.aerofs.daemon.tng.base.IUnicastConnection;
import com.aerofs.daemon.tng.base.IUnicastConnectionService;
import com.aerofs.daemon.tng.xmpp.ISignallingClient;
import com.aerofs.daemon.tng.xmpp.ISignallingService;
import com.aerofs.daemon.tng.xmpp.ISignallingService.SignallingMessage;
import com.aerofs.daemon.tng.xmpp.zephyr.handler.ConnectTunnelHandler;
import com.aerofs.daemon.tng.xmpp.zephyr.handler.ConnectionProxyHandler;
import com.aerofs.daemon.tng.xmpp.zephyr.handler.NetworkStatsMonitor;
import com.aerofs.daemon.tng.xmpp.zephyr.handler.StrictChannelPipeline;
import com.aerofs.lib.Util;
import com.aerofs.base.async.FutureUtil;
import com.aerofs.lib.notifier.SingleListenerNotifier;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.aerofs.proto.Transport.PBZephyrCandidateInfo;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.http.HttpClientCodec;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.util.concurrent.Executor;

public class ZephyrUnicastConnectionService implements IUnicastConnectionService, ISignallingClient
{
    private static final Logger l = Util.l(ZephyrUnicastConnectionService.class);

    private final ISingleThreadedPrioritizedExecutor _executor;
    private final DID _localDID;
    private final InetSocketAddress _zephyrAddress;
    private final Proxy _proxy;
    private final ILinkStateService _networkLinkStateService;
    private final ISignallingService _signallingService;
    private final ChannelFactory _channelFactory;
    private final NetworkStatsMonitor _networkMonitor;
    private final SingleListenerNotifier<IIncomingUnicastConnectionListener> _notifier = SingleListenerNotifier
            .create();

    public static ZephyrUnicastConnectionService getInstance_(
            ISingleThreadedPrioritizedExecutor executor, DID localDID,
            InetSocketAddress zephyrAddress, Proxy proxy,
            ILinkStateService networkLinkStateService, ISignallingService signallingService,
            ChannelFactory channelFactory, INetworkStats stats)
    {
        ZephyrUnicastConnectionService unicastConnectionService = new ZephyrUnicastConnectionService(
                executor, localDID, zephyrAddress, proxy, networkLinkStateService,
                signallingService, channelFactory, stats);

        signallingService.registerSignallingClient_(unicastConnectionService,
                new SignallingMessagePredicate());

        // we don't actually have to register ourselves with the network link-state service

        return unicastConnectionService;
    }

    private ZephyrUnicastConnectionService(ISingleThreadedPrioritizedExecutor executor,
            DID localDID, InetSocketAddress zephyrAddress, Proxy proxy,
            ILinkStateService networkLinkStateService, ISignallingService signallingService,
            ChannelFactory channelFactory, INetworkStats stats)
    {
        _executor = executor;
        _localDID = localDID;
        _zephyrAddress = zephyrAddress;
        _proxy = proxy;
        _networkLinkStateService = networkLinkStateService;
        _signallingService = signallingService;
        _channelFactory = channelFactory;
        _networkMonitor = new NetworkStatsMonitor(stats);
    }

    @Override
    public void start_()
    {

    }

    @Override
    public void setListener_(IIncomingUnicastConnectionListener listener,
            Executor notificationExecutor)
    {
        _notifier.setListener(listener, notificationExecutor);
    }

    @Override
    public IUnicastConnection createConnection_(final DID did)
    {
        ChannelPipeline pipeline = new StrictChannelPipeline();

        // Add network traffic monitor
        pipeline.addLast("monitor", _networkMonitor);

        // Add proxy support to the pipeline if needed
        switch (_proxy.type()) {
        case DIRECT:
            break;

        case HTTP:
            pipeline.addLast("proxy", new ConnectionProxyHandler(_proxy.address()));
            pipeline.addLast("http_codec", new HttpClientCodec());
            pipeline.addLast("tunnel", new ConnectTunnelHandler());
            break;

        default:
            assert false : ("Non-HTTP proxy not supported");
        }

        // Create the new connection
        return ZephyrUnicastConnection.getInstance_(_executor, _localDID, did, _zephyrAddress,
                _channelFactory, pipeline, _networkLinkStateService, _signallingService);
    }

    @Override
    public void signallingChannelConnected_()
    {
        // Noop
    }

    @Override
    public void signallingChannelDisconnected_()
    {
        // Noop
    }

    @Override
    public void processSignallingMessage_(final SignallingMessage message)
    {
        _executor.execute(new Runnable()
        {

            @Override
            public void run()
            {
                assert message.message.getType() == PBTPHeader.Type.ZEPHYR_CANDIDATE_INFO;
                assert message.message.hasZephyrInfo();
                assert message.message.getZephyrInfo().hasSourceZephyrId();

                l.info("zucs received message: " + message);
                l.info("creating new connection...");

                // This connection does not exist, thus we have an incoming
                // connection request
                final ZephyrUnicastConnection newConnection = (ZephyrUnicastConnection) createConnection_(
                        message.did);

                // Connect with the given ZephyrInfo
                ListenableFuture<Void> future = newConnection.connect_(
                        message.message.getZephyrInfo());
                FutureUtil.addCallback(future, new FutureCallback<Void>()
                {
                    @Override
                    public void onSuccess(Void aVoid)
                    {
                        // Notify listeners of the newly connected connection
                        _notifier.notifyOnOtherThreads(new Visitor(message.did, newConnection));
                    }

                    @Override
                    public void onFailure(Throwable throwable)
                    {
                        l.warn("Failed to connect incoming ZephyrUnicastConnection", throwable);
                    }

                }, _executor);
            }

        });
    }

    /**
     * Specifies which SignallingMessage the ZephyrUnicastConnectionService responds to, which is
     * ZEPHYR_CANDIDATE_INFO messages with no destination zid set (or invalid, aka -1). These types
     * of messages are for new connection requests from a peer, so we should handle them.
     */
    private static class SignallingMessagePredicate implements Predicate<SignallingMessage>
    {
        @Override
        public boolean apply(@Nullable SignallingMessage signallingMessage)
        {
            if (signallingMessage.message.getType() != Type.ZEPHYR_CANDIDATE_INFO) {
                return false;
            }

            if (!signallingMessage.message.hasZephyrInfo()) {
                return false;
            }

            PBZephyrCandidateInfo info = signallingMessage.message.getZephyrInfo();
            if (!info.hasDestinationZephyrId()) {
                return true;
            }

            if (info.getDestinationZephyrId() == ZephyrConstants.ZEPHYR_INVALID_CHAN_ID) {
                return true;
            }

            return false;
        }
    }

    @Override
    public void onLinkStateChanged_(ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed, ImmutableSet<NetworkInterface> current,
            ImmutableSet<NetworkInterface> previous)
    {
        // noop for us
    }

    @Override
    public void dumpStat(PBDumpStat template, PBDumpStat.Builder bd)
            throws Exception
    {
        assert false : ("Not yet implemented");
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        assert false : ("Not yet implemented");
    }
}
