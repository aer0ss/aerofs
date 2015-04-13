/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.transport.lib.StreamKey;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.proto.Core.PBCore;

import javax.annotation.Nullable;
import java.io.InputStream;

import static com.aerofs.daemon.core.protocol.CoreProtocolUtil.typeString;

/**
 * Represents a partially-processed incoming packet from the network stack
 * <p/>
 * Users can expect to have access to:
 * <ul>
 *     <li>sender</li>
 *     <li>transport used to send message</li>
 *     <li>message header</li>
 *     <li>message body</li>
 * </ul>
 */
public class DigestedMessage implements ResponseStream
{
    private final PBCore _pb;
    private final InputStream _is;
    private final Endpoint _ep;
    private final UserID _userId;
    @Nullable private final StreamKey _strm;

    /**
     * @param strm may be null if the incoming message is not a stream
     */
    public DigestedMessage(
            PBCore pb,
            InputStream is,
            Endpoint ep,
            UserID userId,
            @Nullable StreamKey strm)
    {
        _pb = pb;
        _is = is;
        _ep = ep;
        _strm = strm;
        _userId = userId;
    }

    public Endpoint ep()
    {
        return _ep;
    }

    public DID did()
    {
        return _ep.did();
    }

    public ITransport tp()
    {
        return _ep.tp();
    }

    public PBCore pb()
    {
        return _pb;
    }

    public InputStream is()
    {
        return _is;
    }

    public UserID user()
    {
        return _userId;
    }

    /**
     * @return null for atomic messages
     */
    public StreamKey streamKey()
    {
        return _strm;
    }

    @Override
    public String toString()
    {
        return "msg:[t:" + typeString(_pb) + " i:" + _is + " ep:" + _ep +
                " u:" + _userId +  " strm:" + _strm + "]";
    }
}
