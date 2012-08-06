package com.aerofs.daemon.event.net;

import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.id.DID;

/*
 *  sessions are used primarily for authentication and identification
 *  of users by the receiving device. The lifetime of a session is solely
 *  determined by the transport providing the session object. From the core's
 *  viewpoint, a session begins by being received from a transport for the first
 *  time, and ends when an EISessionEnded is received
 *
 *  For example, when implementing an HTTP-based transport, HTTP sessions
 *  (via cookies) may be used to back ISession. The session ends when the
 *  corresponding HTTP session expires.
 *
 *  Note that in theory, sessions may persist across devices, online/offline
 *  periods. and different transports. However, when authenticate a session,
 *  the core needs to know to which device and transport the authentication
 *  request shall be sent to. Therefore the interface provides getTransport()
 *  and getDID() methods.
 */

public class Endpoint {

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
