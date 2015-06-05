package com.aerofs.daemon.event.net.rx;

import com.aerofs.ids.UserID;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.event.IEvent;

import java.io.InputStream;

/**
 * Event that signals the receipt of a packet containing the chunk portion of
 * a stream
 */
public class EIChunk implements IEvent, IInputBuffer
{
    /**
     * {@link Endpoint} from which the message was received
     */
    public final Endpoint _ep;

    public final UserID _userID;

    /**
     * The {@link StreamID} identifying the stream to which this chunk belongs
     */
    public final StreamID _streamId;

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
    private final InputStream _is;

    /**
     * Constructor
     *
     * @param ep Endpoint that sent the packet that generated the event
     * @param streamId Stream id of the stream to which this chunk belongs
     * @param seq Sequence number identifying the chunk's index in the current stream
     * @param is <code>InputStream</code> from which the payload can be read
     * framing header
     */
    public EIChunk(Endpoint ep, UserID userID, StreamID streamId, int seq, InputStream is)
    {
        _ep = ep;
        _userID = userID;
        _streamId = streamId;
        _seq = seq;
        _is = is;
    }

    @Override
    public InputStream is()
    {
        return _is;
    }
}
