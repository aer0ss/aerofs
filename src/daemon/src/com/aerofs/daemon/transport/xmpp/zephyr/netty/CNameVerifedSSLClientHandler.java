/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.netty;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.CNameVerificationHandler;
import com.aerofs.base.ssl.SSLEngineFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.ssl.SslHandler;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Bundles both the {@link SslHandler} and the {@link com.aerofs.base.ssl.CNameVerificationHandler}
 * into a simple-to-add object
 */
final class CNameVerifedSSLClientHandler extends SimpleChannelHandler
{
    private final UserID localid;
    private final DID localdid;
    private final SSLEngineFactory sslEngineFactory;

    public CNameVerifedSSLClientHandler(UserID localid, DID localdid, SSLEngineFactory sslEngineFactory)
    {
        this.localid = localid;
        this.localdid = localdid;
        this.sslEngineFactory = sslEngineFactory;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        String ourHandlerName = ctx.getName();
        ctx.getChannel().getPipeline().addBefore(ourHandlerName, "cnameverify", getCNameVerificationHandler());
        ctx.getChannel().getPipeline().addBefore("cnameverify", "ssl", getSslHandler());
        ctx.getPipeline().remove(this);
    }

    private CNameVerificationHandler getCNameVerificationHandler()
    {
        return new CNameVerificationHandler(localid, localdid);
    }

    private SslHandler getSslHandler()
            throws IOException, GeneralSecurityException
    {
        SslHandler handler = new SslHandler(sslEngineFactory.getSSLEngine());
        handler.setCloseOnSSLException(true);
        handler.setEnableRenegotiation(false);
        return handler;
    }
}
