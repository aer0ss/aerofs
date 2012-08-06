package com.aerofs.daemon.core.net;

import java.io.ByteArrayInputStream;

import com.aerofs.daemon.core.net.IncomingStreams.StreamKey;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore;

// a helper, dumb class
//
public class DigestedMessage {

    private final PBCore _pb;
    private final ByteArrayInputStream _is;
    private final SIndex _sidx;
    private final Endpoint _ep;
    private final String _user;
    private final StreamKey _strm;

    public DigestedMessage(PBCore pb, ByteArrayInputStream is, SIndex sidx, Endpoint ep,
            StreamKey strm, String user)
    {
        _pb = pb;
        _is = is;
        _sidx = sidx;
        _ep = ep;
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
}
