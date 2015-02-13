package com.aerofs.daemon.event.net;

import com.aerofs.ids.DID;
import com.aerofs.daemon.transport.ITransport;

public class Endpoint
{
    private final ITransport _tp;
    private final DID _did;

    public Endpoint(ITransport tp, DID did)
    {
        _tp = tp;
        _did = did;
    }

    public ITransport tp()
    {
        return _tp;
    }

    public DID did()
    {
        return _did;
    }

    @Override
    public String toString()
    {
        return _did.toString() + ':' + _tp;
    }

    @Override
    public int hashCode()
    {
        return _tp.hashCode() + _did.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null &&
                _did.equals(((Endpoint)o)._did) && _tp.equals(((Endpoint)o)._tp));
    }
}
