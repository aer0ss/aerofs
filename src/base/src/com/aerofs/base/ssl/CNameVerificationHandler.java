/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ssl;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Mobile.CNameVerificationInfo;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;

import javax.net.ssl.SSLException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

import static org.jboss.netty.channel.Channels.connect;
import static org.jboss.netty.channel.Channels.fireChannelConnected;
import static org.jboss.netty.channel.Channels.future;

/**
 * This class perform CName verification of the SSL certificates on both sides of a connection.
 *
 * The SSL certificates that we use to communicate between peers have their CN field set to a
 * hash of the did + user id. After an SSL connection has been established, this class sends a
 * handshake message with the local user id and did. Upon reception of the handshake message, we
 * calculate the expected remote peer's CName and match it against the remote peer's certificate.
 *
 * Usage:
 * Just add this handler in your netty pipeline after the SSL handler and after any magic number
 * matcher or frame decoder / encoder that you want to use. This class assumes that it will receive
 * full messages.
 *
 * Note: this handler does not time out. Instead, upstream handlers should timeout on the connect
 * future.
 */
public class CNameVerificationHandler extends SimpleChannelHandler
{
    public interface CNameListener
    {
        /**
         * Called after the CName verification succeeds, with the verified user id and did
         */
        public void onPeerVerified(UserID user, DID did);
    }

    private final static Logger l = Loggers.getLogger(CNameVerificationHandler.class);

    private final UserID _user;
    private final DID _did;
    private CNameListener _listener;

    private enum State { Handshaking, Handshaken, Failed }
    private State _state = State.Handshaking; // access must be synchronized on this

    public CNameVerificationHandler(UserID user, DID did)
    {
        _user = user;
        _did = did;
    }

    public void setListener(CNameListener listener)
    {
        _listener = listener;
    }

    @Override
    public void connectRequested(final ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        // Save the original connect future and pass a new one downstream.
        // This will allow us to only fire the channelConnected event for upstream handlers after we
        // verify the cname. Note though that this will *not* prevent channel.isConnected() from
        // returning true for upstream handlers.

        final ChannelFuture originalFuture = e.getFuture();
        ctx.setAttachment(originalFuture);

        ChannelFuture newConnectFuture = future(ctx.getChannel());
        newConnectFuture.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception
            {
                if (!future.isSuccess()) {
                    failConnectFuture(ctx, future.getCause());
                }
            }
        });

        connect(ctx, newConnectFuture, (SocketAddress)e.getValue());
    }

    @Override
    public void channelConnected(final ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        l.trace("sending handshake");

        CNameVerificationInfo verificationInfo = CNameVerificationInfo.newBuilder()
                .setUser(_user.getString())
                .setDid(_did.toPB())
                .build();

        ChannelFuture f = Channels.future(ctx.getChannel());
        ChannelBuffer buf = ChannelBuffers.copiedBuffer(verificationInfo.toByteArray());

        // Send the handshake
        // We need to send the data directly downstream rather than call write() otherwise the
        // message would pass through this class's writeRequested()
        ctx.sendDownstream(new DownstreamMessageEvent(ctx.getChannel(), f, buf, null));
    }

    @Override
    public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        switch (_state) {
        default:          throw new IllegalStateException();
        case Failed:      return;
        case Handshaken:  throw new IllegalStateException("msg received after cname verif done");
        case Handshaking:
            l.debug("received handshake");

            // De-serialize the handshake message
            byte[] message = ((ChannelBuffer) e.getMessage()).array();
            CNameVerificationInfo verificationInfo = CNameVerificationInfo.parseFrom(message);

            // Compute the expected cname
            UserID user = UserID.fromInternalThrowIfNotNormalized(verificationInfo.getUser());
            DID did = new DID(verificationInfo.getDid());
            String expected = BaseSecUtil.getCertificateCName(user, did);

            // Compare against the actual cname from the certificate
            String actual = getPeerCName(ctx);
            if (!expected.equals(actual)) {
                l.warn("cname verification failed. exp:" + expected + " act:" + actual + " usr:" + user + " - " + did.toStringFormal());
                throw new SecurityException("cname verification failed");
            }

            _state = State.Handshaken;
            l.debug("{} cname verified {}", did, user);

            // Notify upstream that the connection has been established

            ctx.getPipeline().remove(this);

            if (_listener != null) _listener.onPeerVerified(user, did);

            ChannelFuture originalFuture = (ChannelFuture)ctx.getAttachment();
            if (originalFuture != null) originalFuture.setSuccess();
            fireChannelConnected(ctx, ctx.getChannel().getRemoteAddress());

            break;
        }
    }

    @Override
    public synchronized void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception
    {
        // If the channel was closed while we where waiting for the handshake to complete, fail
        // the connect future that upstream may be waiting on
        if (_state == State.Handshaking) failConnectFuture(ctx, new ClosedChannelException());

        // forward the event upstream
        super.channelClosed(ctx, e);
    }

    @Override
    public synchronized void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception
    {
        if (_state == State.Failed) return;

        throw new IllegalStateException("cname verification in progress. State: " + _state);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception
    {
        failConnectFuture(ctx, e.getCause());

        // Propagate the exception upstream
        super.exceptionCaught(ctx, e);
    }

    private synchronized void failConnectFuture(ChannelHandlerContext ctx, Throwable reason)
    {
        _state = State.Failed;
        ChannelFuture originalFuture = (ChannelFuture)ctx.getAttachment();
        if (originalFuture != null) originalFuture.setFailure(reason);
    }

    /**
     * @return the value of the CN field from the certificate associated with the given context
     */
    private String getPeerCName(ChannelHandlerContext ctx)
            throws SSLException
    {
        // Get the peer principal
        SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
        String principal = sslHandler.getEngine().getSession().getPeerPrincipal().getName();

        // Get the CN field from the principal
        // A typical principal string for AeroFS certificates looks like this:
        // "CN=occilfnakcejlc[...basically [a-p]], OU=na, O=aerofs.com, ST=CA, C=US"
        final String CN_FIELD = "CN=";
        for (String x : principal.split(",")) {
            if (x.trim().startsWith(CN_FIELD)) return x.trim().substring(CN_FIELD.length());
        }

        throw new SSLException("CN field missing: " + principal);
    }
}
