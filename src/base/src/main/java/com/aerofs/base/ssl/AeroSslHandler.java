package com.aerofs.base.ssl;

import com.aerofs.base.Loggers;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;

import javax.net.ssl.SSLEngine;

/**
 * A custom SSL Handler that extends the netty SSL Handler and still delegates all real work to
 * the netty SSL Handler. By extending SSL Handler we can log and analyze the data we receive.
 *
 * TODO(AS): If the netty AE errors get fixed, we could do away with this class.
 *
 */

public class AeroSslHandler extends SslHandler {

    private static final Logger l = Loggers.getLogger(AeroSslHandler.class);

    public AeroSslHandler(SSLEngine engine) {
        super(engine);
    }

    @Override
    protected Object decode(
            final ChannelHandlerContext ctx, Channel channel, ChannelBuffer in) throws Exception {
        try {
            return super.decode(ctx, channel, in);
        } catch (AssertionError ae) {
            // This in all cases would be super bad. Catching AssertionErrors. Shameful! but we
            // are doing it here to log some data that might be useful to debug and rethrowing it.
            l.info("SSL engine session valid: {}", getEngine().getSession().isValid());
            l.info("SSL engine session creation time: {}", getEngine().getSession().getCreationTime());
            l.info("SSL engine session cipher suite used: {}", getEngine().getSession().getCipherSuite());
            l.info("SSL engine session last accessed time: {}", getEngine().getSession().getLastAccessedTime());
            l.info("SSL engine session protocol: {}", getEngine().getSession().getProtocol());
            l.info("SSL engine inboundDone: {}", getEngine().isInboundDone());
            l.info("SSL engine outboundDone: {}", getEngine().isOutboundDone());
            // This should return value of key properties such as readIndex, writerIndex, capacity
            l.info("Channel buffer string representation: {}", in.toString());
            throw ae;
        }
    }

}