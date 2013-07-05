/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.C;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.AddressResolverHandler;
import com.aerofs.base.net.MagicHeader.ReadMagicHeaderHandler;
import com.aerofs.base.net.MagicHeader.WriteMagicHeaderHandler;
import com.aerofs.base.ssl.CNameVerificationHandler;
import com.aerofs.base.ssl.CNameVerificationHandler.CNameListener;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.daemon.transport.tcp.TCPServerHandler.ITCPServerHandlerListener;
import com.aerofs.lib.LibParam;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.handler.ssl.SslHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

/**
 * Helper class to create client and server bootstraps
 */
class BootstrapFactory
{
    /**
     * This class encapsulates the framing parameters that we use
     */
    static class FrameParams
    {
        public static final int MAGIC_SIZE = 2;        // bytes
        public static final int LENGTH_FIELD_SIZE = 2; // bytes
        public static final int MAX_MESSAGE_SIZE = DaemonParam.MAX_TRANSPORT_MESSAGE_SIZE;
        public static final int HEADER_SIZE = MAGIC_SIZE + LENGTH_FIELD_SIZE;
        public static final byte[] MAGIC_BYTES = ByteBuffer.allocate(C.INTEGER_SIZE).putInt(
                LibParam.CORE_MAGIC).array();
    }

    private final AddressResolverHandler _addressResolver = new AddressResolverHandler(newSingleThreadExecutor());
    private final UserID _localuser;
    private final DID _localdid;
    private final SSLEngineFactory _clientSslEngineFactory;
    private final SSLEngineFactory _serverSslEngineFactory;
    private final TransportStats _stats;

    BootstrapFactory(UserID localuser, DID localdid, SSLEngineFactory clientSslEngineFactory, SSLEngineFactory serverSslEngineFactory, TransportStats stats)
    {
        // Check that the maximum message size is smaller than the maximum number that can be
        // represented using LENGTH_FIELD_SIZE bytes
        checkState(FrameParams.MAX_MESSAGE_SIZE < Math.pow(256, FrameParams.LENGTH_FIELD_SIZE));

        _localuser = localuser;
        _localdid = localdid;
        _clientSslEngineFactory = clientSslEngineFactory;
        _serverSslEngineFactory = serverSslEngineFactory;
        _stats = stats;
    }

    ClientBootstrap newClientBootstrap(ClientSocketChannelFactory channelFactory)
    {
        ClientBootstrap bootstrap = new ClientBootstrap(channelFactory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                TCPClientHandler tcpClientHandler = new TCPClientHandler();

                return Channels.pipeline(
                        _addressResolver,
                        newStatsHandler(),
                        newSslHandler(_clientSslEngineFactory),
                        newFameDecoder(),
                        newLengthFieldPrepender(),
                        newMagicReader(),
                        newMagicWriter(),
                        newCNameVerificationHandler(tcpClientHandler),
                        tcpClientHandler);
            }
        });
        return bootstrap;
    }

    ServerBootstrap newServerBootstrap(ServerSocketChannelFactory channelFactory,
            final ITCPServerHandlerListener listener, final ITCP tcp)
    {
        ServerBootstrap bootstrap = new ServerBootstrap(channelFactory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory()
        {
            @Override
            public ChannelPipeline getPipeline() throws Exception
            {
                TCPServerHandler tcpServerHandler = new TCPServerHandler(listener, tcp);

                return Channels.pipeline(
                        _addressResolver,
                        newStatsHandler(),
                        newSslHandler(_serverSslEngineFactory),
                        newFameDecoder(),
                        newLengthFieldPrepender(),
                        newMagicReader(),
                        newMagicWriter(),
                        newCNameVerificationHandler(tcpServerHandler),
                        tcpServerHandler);
            }
        });
        return bootstrap;
    }

    //
    // Helper methods to create the handlers
    //

    private CNameVerificationHandler newCNameVerificationHandler(CNameListener listener)
    {
        CNameVerificationHandler cnameHandler = new CNameVerificationHandler(_localuser, _localdid);
        cnameHandler.setListener(listener);
        return cnameHandler;
    }

    private SslHandler newSslHandler(SSLEngineFactory sslEngineFactory)
            throws IOException, GeneralSecurityException
    {
        SslHandler sslHandler = new SslHandler(sslEngineFactory.getSSLEngine());
        sslHandler.setCloseOnSSLException(true);
        sslHandler.setEnableRenegotiation(false);
        return sslHandler;
    }

    private LengthFieldPrepender newLengthFieldPrepender()
    {
        return new LengthFieldPrepender(FrameParams.LENGTH_FIELD_SIZE);
    }

    private LengthFieldBasedFrameDecoder newFameDecoder()
    {
        return new LengthFieldBasedFrameDecoder(FrameParams.MAX_MESSAGE_SIZE, 0,
                FrameParams.LENGTH_FIELD_SIZE, 0, FrameParams.LENGTH_FIELD_SIZE);
    }

    private ReadMagicHeaderHandler newMagicReader()
    {
        return new ReadMagicHeaderHandler(FrameParams.MAGIC_BYTES);
    }

    private WriteMagicHeaderHandler newMagicWriter()
    {
        return new WriteMagicHeaderHandler(FrameParams.MAGIC_BYTES);
    }

    private IOStatsHandler newStatsHandler()
    {
        return new IOStatsHandler(_stats);
    }
}
