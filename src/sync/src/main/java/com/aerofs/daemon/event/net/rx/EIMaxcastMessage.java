package com.aerofs.daemon.event.net.rx;

import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.event.IEvent;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * {@link IEvent} that is created and generated when a maxcast packet is received
 * from a device
 */
public class EIMaxcastMessage implements IEvent, IInputBuffer
{
    /**
     * {@link Endpoint} from which this maxcast packet was sent
     */
    public final Endpoint _ep;

    /**
     * {@link java.io.InputStream} from which the payload of the maxcast packet
     * can be read
     */
    private final ByteArrayInputStream _is;

    /**
     * Original length of the packet on the wire, including
     * transport framing headers and the payload
     */
    private final int _wirelen;

    /**
     * Constructor
     *
     * @param ep Endpoint that sent the maxcast message
     * @param is InputStream from which the payload can be read
     * @param wirelen Original length of the packet on the wire (including
     */
    public EIMaxcastMessage(Endpoint ep, ByteArrayInputStream is, int wirelen)
    {
        _ep = ep;
        _is = is;
        _wirelen = wirelen;
    }

    @Override
    public InputStream is()
    {
        return _is;
    }

    @Override
    public int wireLength()
    {
        return _wirelen;
    }
}
