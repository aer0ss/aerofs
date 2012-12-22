package com.aerofs.daemon.event.net.rx;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.lib.id.StreamID;

// release the transport resource for this stream. Upon receiving chunks for
// an ended stream, the receiver's transport should send an abortion
// to the sender such that the sender will release transport resource for this
// stream and throw an ExStreamInvalid exception when it attempts to send the
// next chunk.
//
// The transport doesn't notify the sender.
//
public class EORxEndStream extends AbstractEBIMC
{

    public final DID _did;
    public final StreamID _sid;

    public EORxEndStream(DID did, StreamID sid, IIMCExecutor imce)
    {
        super(imce);
        _did = did;
        _sid = sid;
    }

    @Override
    public String toString()
    {
        return "rxEndStrm " + _did + ':' + _sid;
    }
}
