/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.event.net.tng.rx;

import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.net.rx.IInputBuffer;
import com.aerofs.daemon.event.net.tng.Endpoint;
import com.aerofs.base.id.SID;

import java.io.ByteArrayInputStream;

/**
 * {@link com.aerofs.daemon.event.IEvent} that is created and generated when a maxcast packet is
 * received from a device
 */
public class EIMaxcastMessage implements IEvent, IInputBuffer
{
    /**
     * {@link com.aerofs.daemon.event.net.Endpoint} from which this maxcast packet was sent
     */
    public final Endpoint _ep;

    /**
     * {@link java.io.InputStream} from which the payload of the maxcast packet can be read
     */
    private final ByteArrayInputStream _is;

    /**
     * Original length of the packet on the wire, including transport framing headers and the
     * payload
     */
    private final int _wirelen;

    /**
     * Store address to which this payload is related
     */
    public final SID _sid;

    /**
     * Constructor
     *
     * @param ep Endpoint that sent the maxcast message
     * @param sid Store id to which the maxcast packet is related
     * @param is InputStream from which the payload can be read
     * @param wirelen Original length of the packet on the wire (including <code>ITransport</code>
     * framing header
     */
    public EIMaxcastMessage(Endpoint ep, SID sid, ByteArrayInputStream is, int wirelen)
    {
        _ep = ep;
        _sid = sid;
        _is = is;
        _wirelen = wirelen;
    }

    @Override
    public ByteArrayInputStream is()
    {
        return _is;
    }

    @Override
    public int wireLength()
    {
        return _wirelen;
    }
}
