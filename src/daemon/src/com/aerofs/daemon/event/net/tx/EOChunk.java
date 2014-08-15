package com.aerofs.daemon.event.net.tx;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.net.IOutgoingStreamFeedback;
import com.aerofs.daemon.core.net.TransferStatisticsManager;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.event.IEvent;

import static com.google.common.base.Preconditions.checkState;

//N.B. streams are always at background priority to allow atomic messages go first
//
public class EOChunk implements IEvent, IResultWaiter, IOutputBuffer
{
    private final IOutgoingStreamFeedback _stream;
    private final String _transportId;
    private final TransferStatisticsManager _tsm;

    public final StreamID _streamId;
    public final int _seq; // TODO remove it. see IUnicastOutputLayer.sendOutgoingStream_'s comment
    public final byte[] _bs;
    public final DID _did;

    private Exception _e;

    public EOChunk(
            StreamID streamId,
            IOutgoingStreamFeedback stream,
            int seq,
            DID did,
            byte[] bs,
            ITransport tp,
            TransferStatisticsManager tsm)
    {
        _streamId = streamId;
        _stream = stream;
        _seq = seq;
        _bs = bs;
        _did = did;
        _transportId = tp.id();
        _tsm = tsm;

        // Technically we should only call this when EOChunk actually is enqueued
        _stream.incChunkCount();
    }

    @Override
    public byte[] byteArray() {
        return _bs;
    }

    public Exception exception()
    {
        return _e;
    }

    @Override
    public void okay()
    {
        _stream.decChunkCount();
        _tsm.markTransferred(_transportId, _bs.length);
    }

    @Override
    public void error(Exception e)
    {
        checkState(_e == null);
        _e = e;
        _stream.decChunkCount();
        _stream.setFirstFailedChunk(this);
        _tsm.markErrored(_transportId, _bs.length);
    }

    @Override
    public String toString()
    {
        return "send outgoing chunk stream:" + _streamId;
    }
}
