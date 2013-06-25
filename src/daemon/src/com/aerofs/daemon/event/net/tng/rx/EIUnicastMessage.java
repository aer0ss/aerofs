package com.aerofs.daemon.event.net.tng.rx;

import com.aerofs.daemon.event.net.rx.IInputBuffer;
import com.aerofs.daemon.event.net.tng.Endpoint;
import com.aerofs.lib.event.IEvent;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Signals the receipt of a unicast atomic message from an {@link com.aerofs.daemon.event.net.Endpoint}.
 * Unicast atomic messages provides no flow control. Use streams ({@link
 * com.aerofs.daemon.event.net.rx.EIStreamBegun} and {@link com.aerofs.daemon.event.net.tng.rx.EIChunk})
 * if flow control is required.
 */
public class EIUnicastMessage implements IEvent, IInputBuffer
{
    /**
     * {@link com.aerofs.daemon.event.net.Endpoint} from which the message was received
     */
    public final Endpoint _ep;

    /**
     * {@link java.io.InputStream} to use to read the application payload. <b>IMPORTANT:</b> the
     * transport framing header has already been read from this <code>InputStream</code>
     */
    public final ByteArrayInputStream _is;

    /**
     * Original length of the packet transferred over the wire (includes both the length of the
     * transport framing header and the payload)
     */
    public final int _wirelen;

    /**
     * Constructor
     *
     * @param ep Endpoint that sent the packet that generated the event
     * @param is <code>InputStream</code> from which the payload can be read
     * @param wirelen Number of bytes (including the <code>ITransport</code> framing header
     */
    public EIUnicastMessage(Endpoint ep, ByteArrayInputStream is, int wirelen)
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
