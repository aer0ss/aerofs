/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.transport.lib.TransportProtocolUtil;
import com.aerofs.proto.Transport.PBTPHeader;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;

import java.io.IOException;
import java.io.InputStream;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class is a higher-level representation of an incoming Netty message.
 * It wraps around a ChannelBuffer, and extracts meaningful information such as the PBTPHeader and
 * so on out of it, so that upstream handlers can deal with that instead of the raw ChannelBuffer.
 */
public final class TransportMessage
{
    private final InputStream payloadInputStream;
    private final PBTPHeader header;
    private final DID did;
    private final UserID userID;

    public TransportMessage(ChannelBuffer channelBuffer, DID did, UserID userID)
            throws IOException
    {
        this.did = checkNotNull(did);
        this.userID = checkNotNull(userID);
        this.payloadInputStream = new ChannelBufferInputStream(channelBuffer);
        this.header = TransportProtocolUtil.processUnicastHeader(payloadInputStream);
    }

    public PBTPHeader getHeader()
    {
        return header;
    }

    /**
     * @return cname-verified did
     */
    public DID getDID()
    {
        return did;
    }

    /**
     * @return cname-verified user id
     */
    public UserID getUserID()
    {
        return userID;
    }

    /**
     * @return true if this message is a datagram, stream header or stream chunk. false otherwise.
     */
    public boolean isPayload()
    {
        return TransportProtocolUtil.isPayload(header);
    }

    /**
     * @return an input stream that contains the payload bytes that have been sent after the PBTPHeader
     */
    public InputStream getPayload()
    {
        return payloadInputStream;
    }
}
