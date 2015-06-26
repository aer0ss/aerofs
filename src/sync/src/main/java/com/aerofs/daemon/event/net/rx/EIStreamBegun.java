package com.aerofs.daemon.event.net.rx;

import com.aerofs.ids.UserID;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.event.IEvent;

import java.io.InputStream;

public class EIStreamBegun implements IEvent, IInputBuffer {
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
     * {@link InputStream} to use to read the application payload.
     * <b>IMPORTANT:</b> the {@link com.aerofs.daemon.transport.ITransport}
     * framing header has already been read from this <code>InputStream</code>
     */
    private final InputStream _is;

    /**
     * Constructor
     *
     * For parameter information, {@see EIChunk.EIChunk}
     */
    public EIStreamBegun(Endpoint ep, UserID userID, StreamID strid, InputStream is)
    {
        _ep = ep;
        _userID = userID;
        _streamId = strid;
        _is = is;
    }

    @Override
    public InputStream is()
    {
        return _is;
    }
}

