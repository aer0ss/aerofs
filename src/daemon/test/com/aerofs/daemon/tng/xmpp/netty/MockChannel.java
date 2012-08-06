package com.aerofs.daemon.tng.xmpp.netty;

import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.HeapChannelBufferFactory;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.SocketChannelConfig;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * A {@link Channel} that simply sends events down its associated
 * {@link ChannelPipeline}.
 * Connections are established when the events reach the attached
 * {@link MockChannelEventSink}.
 */
public class MockChannel extends AbstractChannel implements SocketChannel
{
    private volatile InetSocketAddress _localAddress;
    private volatile InetSocketAddress _remoteAddress;
    private final SocketChannelConfig _config;

    public static MockChannel createChannel(ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink)
    {
        return new MockChannel(null, factory, pipeline, sink);
    }

    protected MockChannel(Channel parent, ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink)
    {
        super(parent, factory, pipeline, sink);

        _config = new SocketChannelConfig()
        {

            private ChannelBufferFactory _factory =
                    new HeapChannelBufferFactory();

            @Override
            public void setOptions(Map<String, Object> options)
            {
            }

            @Override
            public boolean setOption(String name, Object value)
            {
                return true;
            }

            @Override
            public ChannelBufferFactory getBufferFactory()
            {
                return _factory;
            }

            @Override
            public void setBufferFactory(ChannelBufferFactory bufferFactory)
            {

            }

            @Override
            public ChannelPipelineFactory getPipelineFactory()
            {
                return null;
            }

            @Override
            public void setPipelineFactory(
                    ChannelPipelineFactory pipelineFactory)
            {

            }

            @Override
            public int getConnectTimeoutMillis()
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public void setConnectTimeoutMillis(int connectTimeoutMillis)
            {
                // TODO Auto-generated method stub

            }

            @Override
            public boolean isTcpNoDelay()
            {
                return false;
            }

            @Override
            public void setTcpNoDelay(boolean tcpNoDelay)
            {

            }

            @Override
            public int getSoLinger()
            {
                return 0;
            }

            @Override
            public void setSoLinger(int soLinger)
            {
                // TODO Auto-generated method stub

            }

            @Override
            public int getSendBufferSize()
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public void setSendBufferSize(int sendBufferSize)
            {
                // TODO Auto-generated method stub

            }

            @Override
            public int getReceiveBufferSize()
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public void setReceiveBufferSize(int receiveBufferSize)
            {
                // TODO Auto-generated method stub

            }

            @Override
            public boolean isKeepAlive()
            {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public void setKeepAlive(boolean keepAlive)
            {
                // TODO Auto-generated method stub

            }

            @Override
            public int getTrafficClass()
            {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public void setTrafficClass(int trafficClass)
            {
                // TODO Auto-generated method stub

            }

            @Override
            public boolean isReuseAddress()
            {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public void setReuseAddress(boolean reuseAddress)
            {
                // TODO Auto-generated method stub

            }

            @Override
            public void setPerformancePreferences(int connectionTime,
                    int latency, int bandwidth)
            {
                // TODO Auto-generated method stub

            }

        };
    }

    @Override
    public SocketChannelConfig getConfig()
    {
        return _config;
    }

    @Override
    public boolean isBound()
    {
        return _localAddress != null;
    }

    @Override
    public boolean isConnected()
    {
        return _remoteAddress != null;
    }

    @Override
    public InetSocketAddress getLocalAddress()
    {
        return _localAddress;
    }

    @Override
    public InetSocketAddress getRemoteAddress()
    {
        return _remoteAddress;
    }

    public synchronized void setRemoteAddress(InetSocketAddress address)
    {
        _remoteAddress = address;
    }

    public synchronized void setLocalAddress(InetSocketAddress address)
    {
        _localAddress = address;
    }

    @Override
    public boolean setClosed()
    {
        _localAddress = null;
        _remoteAddress = null;
        return super.setClosed();
    }
}
