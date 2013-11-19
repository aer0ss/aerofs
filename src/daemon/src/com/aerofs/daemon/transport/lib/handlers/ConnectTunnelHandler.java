package com.aerofs.daemon.transport.lib.handlers;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.jboss.netty.channel.Channels.connect;
import static org.jboss.netty.channel.Channels.fireChannelConnected;
import static org.jboss.netty.channel.Channels.fireExceptionCaught;
import static org.jboss.netty.channel.Channels.write;

public final class ConnectTunnelHandler extends SimpleChannelHandler
{
    private InetSocketAddress addressToConnectTo = null;
    private ChannelFuture originalConnectFuture = null;

    @Override
    public void connectRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
        throws Exception
    {
        // Grab the connect Future so we can set success when the HTTP CONNECT
        // message is responded to with HTTP/1.1 200 OK
        originalConnectFuture = e.getFuture();
        ChannelFuture future = new DefaultChannelFuture(e.getChannel(), false);
        connect(ctx, future, (SocketAddress)e.getValue());
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
        throws Exception
    {
        checkNotNull(originalConnectFuture);

        // Catch the address we need to CONNECT to
        addressToConnectTo = (InetSocketAddress) e.getValue();

        HttpRequest request = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.CONNECT,
                addressToConnectTo.getAddress().getHostAddress() + ":" + addressToConnectTo.getPort());

        ChannelFuture f = new DefaultChannelFuture(e.getChannel(), false);
        f.addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future)
                throws Exception
            {
                if (!future.isSuccess()) {
                    notifyCallerOfConnectFailure(future.getCause());
                }

                // On success, it is still unclear if the connection will
                // succeed. The Proxy needs to respond with HTTP response code
                // 200
            }

        });
        write(ctx, f, request);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (!(e.getMessage() instanceof HttpResponse)) {
            throw new IOException("ConnectHandler can only receive HttpResponse messages");
        }

        ctx.getPipeline().remove(this);

        checkNotNull(addressToConnectTo);
        checkNotNull(originalConnectFuture);

        HttpResponse response = (HttpResponse) e.getMessage();
        if (response.getStatus().equals(HttpResponseStatus.OK)) { // tunnel established
            originalConnectFuture.setSuccess();
            fireChannelConnected(ctx, addressToConnectTo); // ok to use 'fire' here, because we're already inbound
        } else { // error in establishing the tunnel
            Throwable cause = new IOException("proxy failed to connect: " + response.getStatus());
            notifyCallerOfConnectFailure(cause); // notify the original caller
            fireExceptionCaught(ctx, cause); // fire the exception up the pipeline
        }
    }

    private void notifyCallerOfConnectFailure(Throwable cause)
    {
        originalConnectFuture.setFailure(cause);
    }
}
