package com.aerofs.daemon.event.net.tx;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.net.IOutgoingStreamFeedback;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.lib.id.StreamID;

//N.B. streams are always at background priority to allow atomic messages go first
//
public class EOChunk extends AbstractEBIMC implements IOutputBuffer {

    public final StreamID _streamId;
    public final int _seq; // TODO remove it. see IUnicastOutputLayer.sendOutgoingStream_'s comment
    public final byte[] _bs;
    public final DID _did;
    private final IOutgoingStreamFeedback _stream;

    public EOChunk(StreamID streamId, IOutgoingStreamFeedback stream, int seq, DID did, byte[] bs, IIMCExecutor imce)
    {
        super(imce);
        _streamId = streamId;
        _stream = stream;
        _seq = seq;
        _bs = bs;
        _did = did;

        // Technically we should only call this when EOChunk actually is enqueued
        _stream.incChunkCount();
    }

    @Override
    public byte[] byteArray() {

        return _bs;
    }

    @Override
    public void okay()
    {
        super.okay();
        _stream.decChunkCount();
    }

    @Override
    public void error(Exception e)
    {
        super.error(e);
        _stream.decChunkCount();
        _stream.setFirstFailedChunk(this);
    }

    @Override
    public String toString()
    {
        return "send outgoing chunk stream:" + _streamId;
    }
}
