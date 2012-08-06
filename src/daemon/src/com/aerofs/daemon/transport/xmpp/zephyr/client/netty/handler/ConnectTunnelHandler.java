package com.aerofs.daemon.transport.xmpp.zephyr.client.netty.handler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

public class ConnectTunnelHandler
        extends SimpleChannelHandler {

    private InetSocketAddress _address;
    private ChannelFuture _clientConnectFuture;

    public ConnectTunnelHandler()
    {
        _address = null;
        _clientConnectFuture = null;
    }

    @Override
    public void connectRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
        throws Exception
    {
        // Grab the connect Future so we can set success when the HTTP CONNECT
        // message is responded to with HTTP/1.1 200 OK
        _clientConnectFuture = e.getFuture();
        ChannelFuture future = new DefaultChannelFuture(e.getChannel(), false);
        Channels.connect(ctx, future, (SocketAddress) e.getValue());
    }


    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
        throws Exception
    {
        assert _clientConnectFuture != null;

        // Catch the address we need to CONNECT to
        _address = (InetSocketAddress) e.getValue();

        HttpRequest request = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.CONNECT,
                _address.getAddress().getHostAddress() + ":" + _address.getPort());

        ChannelFuture f = new DefaultChannelFuture(e.getChannel(), false);
        f.addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future)
                throws Exception
            {
                if (!future.isSuccess()) {
                    // The connection failed, so set propagate the exception
                    // to the intercepted future
                    _clientConnectFuture.setFailure(future.getCause());
                }

                // On success, it is still unclear if the connection will
                // succeed. The Proxy needs to respond with HTTP response code
                // 200
            }

        });
        Channels.write(ctx, f, request);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (!(e.getMessage() instanceof HttpResponse)) {
            throw new IOException("ConnectHandler can only receive HttpResponse messages");
        }

        ctx.getPipeline().remove(this);

        assert _address != null;
        assert _clientConnectFuture != null;

        HttpResponse response = (HttpResponse) e.getMessage();
        if (response.getStatus().equals(HttpResponseStatus.OK)) {
            // The tunnel is established
            _clientConnectFuture.setSuccess();

            Channels.fireChannelConnected(ctx, _address);
        } else {
            // Error in establishing tunnel connection
            Throwable cause = new IOException(
                    "Proxy failed to connect: " + response.getStatus());

            // Notify the future listener
            _clientConnectFuture.setFailure(cause);

            // Fire the exception up the pipeline
            Channels.fireExceptionCaught(ctx, cause);
        }
    }

}
