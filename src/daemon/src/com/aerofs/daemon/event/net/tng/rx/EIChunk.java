package com.aerofs.daemon.event.net.tng.rx;

import com.aerofs.lib.event.IEvent;
import com.aerofs.daemon.event.net.rx.IInputBuffer;
import com.aerofs.daemon.event.net.tng.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IIncomingStream;

import java.io.ByteArrayInputStream;

/**
 * Event that signals the receipt of a packet containing the chunk portion of
 * a stream
 */
public class EIChunk implements IEvent, IInputBuffer
{
    /**
     * {@link com.aerofs.daemon.event.net.Endpoint} from which the message was received
     */
    public final Endpoint _ep;

    /**
     * The {@link IIncomingStream} object itself
     */
    public final IIncomingStream _stream;

    /**
     * The {@link com.aerofs.daemon.lib.id.StreamID} identifying the stream to which this chunk belongs
     */
    public final StreamID _strid;

    /**
     * The sequence number of the current chunk, starting at 1 (chunk 0
     * is the one that signals the beginning of a stream)
     */
    public final int _seq;

    /**
     * {@link java.io.InputStream} to use to read the application payload.
     * <b>IMPORTANT:</b> the {@link com.aerofs.daemon.transport.ITransport}
     * framing header has already been read from this <code>InputStream</code>
     */
    public final ByteArrayInputStream _is;

    /**
     * Original length of the packet transferred over the wire (includes both
     * the length of the transport framing header and the payload)
     */
    public final int _wirelen;

    /**
     *
     * Constructor
     *
     * @param ep Endpoint that sent the packet that generated the event
     * the packet related to this event
     * @param seq Sequence number identifying the chunk's index in the current stream
     * @param is <code>InputStream</code> from which the payload can be read
     * @param wirelen Number of bytes (including the <code>ITransport</code>
     * framing header
     */
    public EIChunk(Endpoint ep, IIncomingStream stream, int seq, ByteArrayInputStream is, int wirelen)
    {
        _ep = ep;
        _stream = stream;
        _strid = _stream.getStreamId_();
        _seq = seq;
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
