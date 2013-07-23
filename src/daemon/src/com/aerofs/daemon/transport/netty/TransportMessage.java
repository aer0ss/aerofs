/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.netty;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.transport.lib.TPUtil;
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
public class TransportMessage
{
    private final InputStream _is;
    private final PBTPHeader _header;
    private final DID _did;
    private final UserID _userID;

    public TransportMessage(ChannelBuffer channelBuffer, DID did, UserID userID)
            throws IOException
    {
        _did = checkNotNull(did);
        _userID = checkNotNull(userID);
        _is = new ChannelBufferInputStream(channelBuffer);
        _header = TPUtil.processUnicastHeader(_is);
    }

    public PBTPHeader getHeader()
    {
        return _header;
    }

    /**
     * @return cname-verified did
     */
    public DID getDID()
    {
        return _did;
    }

    /**
     * @return cname-verified user id
     */
    public UserID getUserID()
    {
        return _userID;
    }

    public boolean isPayload()
    {
        return TPUtil.isPayload(_header);
    }

    /**
     * @return an input stream that contains the payload bytes that have been sent after the PBTPHeader
     */
    public InputStream getPayload()
    {
        return _is;
    }
}
