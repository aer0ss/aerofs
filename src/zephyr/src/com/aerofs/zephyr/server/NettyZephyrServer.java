/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.zephyr.server;

import com.aerofs.lib.Loggers;
import com.aerofs.lib.net.TraceHandler;
import com.aerofs.lib.net.ZephyrConstants;
import com.aerofs.lib.net.ZephyrPipeHandler;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class NettyZephyrServer implements ChannelPipelineFactory
{
    private static final Logger LOGGER = Loggers.getLogger(NettyZephyrServer.class);

    private static final boolean ENABLE_FLOW_CONTROL = true;

    public static void main(String args[])
    {
        int port = Integer.getInteger("port", 9999);
        NettyZephyrServer server = new NettyZephyrServer();
        server.start(port);
    }

    ChannelFactory _factory;
    Channel _channel;
    ChannelGroup _channelGroup = new DefaultChannelGroup("zephyr");

    AtomicInteger _nextId = new AtomicInteger();
    ConcurrentMap<Integer, ZephyrConnection> _connectionMap =
            new ConcurrentHashMap<Integer, ZephyrConnection>();

    public void start(int port)
    {
        _factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool(), 1);
        ServerBootstrap bootstrap = new ServerBootstrap(_factory);
        bootstrap.setOption("localAddress", new InetSocketAddress(port));
        bootstrap.setOption("reuseAddress", true);
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);
        bootstrap.setPipelineFactory(this);
        _channel = bootstrap.bind();
        LOGGER.info("listening on {}", _channel.getLocalAddress());
        _channelGroup.add(_channel);
    }

    public void stop()
    {
        _channelGroup.close();
        _factory.releaseExternalResources();
    }

    private int nextId()
    {
        int id;
        do {
            id = _nextId.getAndIncrement();
        } while (id == ZephyrConstants.ZEPHYR_INVALID_CHAN_ID);
        return id;
    }

    @Override
    public ChannelPipeline getPipeline()
            throws Exception
    {
        ZephyrConnection conn = new ZephyrConnection();
        int id;
        do {
            id = nextId();
            conn._id = id;
        } while (_connectionMap.putIfAbsent(id, conn) != null);
        return conn.getPipeline();
    }

    class ZephyrConnection
    {
        int _id;
        Channel _channel;
        ZephyrConnection _peer;
        boolean _closed;

        Object _trafficLock = new Object();

        private void init(Channel channel) {
            _channel = channel;
            _channelGroup.add(channel);
        }

        ZephyrConnection conn()
        {
            return this;
        }

        @Override
        public String toString()
        {
            return _id + "@" + ((_channel == null) ? (null) : (_channel.getRemoteAddress()));
        }

        private class ZephyrConnectionHandler extends SimpleChannelUpstreamHandler
        {
            @Override
            public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
                    throws Exception
            {
                init(ctx.getChannel());
                super.channelOpen(ctx, e);
            }

            @Override
            public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
                    throws Exception
            {
//                LOGGER.info("connection {}", conn());
//                _channel.write(_id);
                ctx.sendDownstream(new ZephyrPipeHandler.SendZephyrIDEvent(e.getChannel(), _id));
                super.channelConnected(ctx, e);
            }

            @Override
            public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
                    throws Exception
            {
                if (e instanceof ZephyrPipeHandler.ReceiveZephyrIDEvent) {
                    int zid = ((ZephyrPipeHandler.ReceiveZephyrIDEvent)e).getZephyrID();
                    handleReceiveZid(zid);
                } else {
                    super.handleUpstream(ctx, e);
                }
            }

            @Override
            public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
                    throws Exception
            {
//                LOGGER.info("connection {} disconnected", conn());
                e.getChannel().close();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                    throws Exception
            {
//                LOGGER.info("connection " + conn() + " caught exception", e.getCause());
                e.getChannel().close();
            }
        }

        public ChannelPipeline getPipeline()
        {
            ChannelPipeline p = Channels.pipeline();
            p.addLast("tracer", new TraceHandler());
            p.addLast("zephyr", new ZephyrPipeHandler());
            p.addLast("zephyrConnector", new ZephyrConnectionHandler());
            p.addLast("copier", new SimpleChannelUpstreamHandler() {
                @Override
                public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                {
                    ChannelBuffer buffer = (ChannelBuffer)e.getMessage();
                    ZephyrConnection peer = _peer;
                    assert peer != null;
                    synchronized (_trafficLock) {
                        peer._channel.write(e.getMessage());
                        if (!peer._channel.isWritable()) {
//                            LOGGER.debug("{}: not writable", _peer);
                            if (ENABLE_FLOW_CONTROL) {
                                _channel.setReadable(false);
                                // check for race condition
                                // TODO: execute on i/o thread?
                                if (peer._channel.isWritable()) {
                                    _channel.setReadable(true);
                                }
                            }
                        }
                    }
                }

                @Override
                public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e)
                        throws Exception
                {
                    ZephyrConnection peer = getPeer();
                    if (ENABLE_FLOW_CONTROL && peer != null) {
                        synchronized (peer._trafficLock) {
                            if (_channel.isWritable()) {
//                                LOGGER.debug("{} writable", conn());
                                peer._channel.setReadable(true);
                            }
                        }
                    }
                    super.channelInterestChanged(ctx, e);
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                        throws Exception
                {
                    e.getChannel().close();
                }

                @Override
                public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
                        throws Exception
                {
                    ZephyrConnection peer = getPeer();
                    if (peer != null) peer._channel.close();
                }
            });
            return p;
        }

        synchronized ZephyrConnection getPeer()
        {
            return _peer;
        }

        void handleReceiveZid(int zid)
        {
            synchronized (this) {
                ZephyrConnection peer = _connectionMap.remove(zid);
                _peer = peer;
            }
            if (_peer == null) {
                close();
                return;
            }
            LOGGER.info("{}: got peer {}", conn(), _peer.conn());
            boolean peerClosed;
            synchronized (_peer) {
                peerClosed = _peer._closed;
            }
            if (peerClosed) close();
        }

        void close()
        {
            _channel.close();
        }
    }

}
