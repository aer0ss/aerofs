package com.aerofs.daemon.event.net.rx;

import com.aerofs.ids.UserID;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.event.IEvent;

import java.io.InputStream;

/**
 * Signals the receipt of a unicast atomic message from an {@link Endpoint}.
 * Unicast atomic messages provides no flow control. Use streams ({@link EIStreamBegun}
 * and {@link EIChunk}) if flow control is required.
 */
public class EIUnicastMessage implements IEvent, IInputBuffer
{
    /**
     * {@link Endpoint} from which the message was received
     */
    public final Endpoint _ep;

    public final UserID _userID;

    /**
     * {@link java.io.InputStream} to use to read the application payload.
     * <b>IMPORTANT:</b> the transport framing header has already been read
     * from this <code>InputStream</code>
     */
    private final InputStream _is;

    /**
     * Original length of the packet transferred over the wire (includes both
     * the length of the transport framing header and the payload)
     */
    private final int _wirelen;

    /**
     * Constructor
     *
     * @param ep Endpoint that sent the packet that generated the event
     * @param is <code>InputStream</code> from which the payload can be read
     * @param wirelen Number of bytes (including the <code>ITransport</code>
     * framing header
     */
    public EIUnicastMessage(Endpoint ep, UserID userID, InputStream is, int wirelen)
    {
        _ep = ep;
        _userID = userID;
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
