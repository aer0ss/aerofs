/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import java.io.ByteArrayInputStream;

import com.aerofs.daemon.core.net.IncomingStreams.StreamKey;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore;

import javax.annotation.Nullable;

import static com.aerofs.daemon.core.CoreUtil.typeString;

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
public class DigestedMessage
{
    private final PBCore _pb;
    private final ByteArrayInputStream _is;
    private final Endpoint _ep;
    private final SIndex _sidx;
    private final String _user;
    @Nullable private final StreamKey _strm;

    /**
     * @param strm may be null if the incoming message is not a stream
     */
    public DigestedMessage(
            PBCore pb,
            ByteArrayInputStream is,
            Endpoint ep,
            SIndex sidx,
            String user, // FIXME (AG): Should have a User type
            @Nullable StreamKey strm)
    {
        _pb = pb;
        _is = is;
        _ep = ep;
        _sidx = sidx;
        _strm = strm;
        _user = user;
    }

    public SIndex sidx()
    {
        return _sidx;
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

    public ByteArrayInputStream is()
    {
        return _is;
    }

    public String user()
    {
        return _user;
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
        return "msg:[t:" + typeString(_pb) + " i:" + _is + " sidx:" + _sidx + " ep:" + _ep +
                " u:" + _user +  " strm:" + _strm + "]";
    }
}
