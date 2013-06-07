package com.aerofs.daemon.event.net.tx;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.lib.id.StreamID;

//N.B. streams are always at background priority to allow atomic messages
//go first
//
public class EOChunk extends AbstractEBIMC implements IOutputBuffer {

    public final StreamID _streamId;
    public final int _seq; // TODO remove it. see IUnicastOutputLayer.sendOutgoingStream_'s comment
    public final byte[] _bs;
    public final DID _did;

    public EOChunk(StreamID streamId, int seq, DID did, byte[] bs, IIMCExecutor imce)
    {
        super(imce);
        _streamId = streamId;
        _seq = seq;
        _bs = bs;
        _did = did;
    }

    @Override
    public byte[] byteArray() {

        return _bs;
    }

    @Override
    public String toString()
    {
        return "send outgoing chunk stream:" + _streamId;
    }
}
