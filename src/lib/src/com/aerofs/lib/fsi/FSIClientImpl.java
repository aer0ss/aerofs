package com.aerofs.lib.fsi;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import com.aerofs.lib.C;
import com.aerofs.lib.Profiler;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.proto.Fsi.PBFSICall;
import com.aerofs.proto.Fsi.PBFSIReply;

class FSIClientImpl {

    private final Socket _s;
    private final DataInputStream _sin;
    private final DataOutputStream _sout;
    private final Profiler _p = new Profiler();

    public FSIClientImpl(InetAddress addr, int port) throws IOException
    {
        _s = new Socket(addr, port);

        _sin = new DataInputStream(
                new BufferedInputStream(_s.getInputStream()));

        _sout = new DataOutputStream(
                new BufferedOutputStream(_s.getOutputStream()));
    }

    public void send_(PBFSICall call) throws IOException
    {
        Util.writeMessage(_sout, C.FSI_MAGIC, call.toByteArray());
    }

    public PBFSIReply rpc_(PBFSICall call) throws Exception
    {
        _p.start();
        try {
            send_(call);
            byte[] bs = Util.readMessage(_sin, C.FSI_MAGIC, Integer.MAX_VALUE);
            PBFSIReply reply = PBFSIReply.parseFrom(bs);

            if (reply.hasException()) throw Exceptions.fromPB(reply.getException());

            return reply;
        } finally {
            _p.stop();
        }
    }

    public PBFSIReply rpc_(PBFSICall call, int timeout) throws Exception
    {
        int prev = _s.getSoTimeout();
        _s.setSoTimeout(timeout);
        try {
            return rpc_(call);
        } finally {
            _s.setSoTimeout(prev);
        }
    }

    public void close_()
    {
        try {
            _s.close();
        } catch (IOException e) {
            Util.l(this).warn("cannot close " + FSIClientImpl.class.getName() +
                    ". ignored: " + Util.e(e));
        }
    }
}
