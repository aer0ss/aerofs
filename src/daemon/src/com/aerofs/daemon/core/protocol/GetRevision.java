package com.aerofs.daemon.core.protocol;

import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.core.net.OutgoingStreams;
import com.aerofs.daemon.core.net.OutgoingStreams.OutgoingStream;
import com.aerofs.daemon.core.net.RPC;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.RevInputStream;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.DID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBGetRevisionCall;
import com.aerofs.proto.Core.PBGetRevisionReply;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;

public class GetRevision
{
    private final RPC _rpc;
    private final IPhysicalStorage _ps;
    private final OutgoingStreams _oss;
    private final Metrics _m;
    private final IncomingStreams _iss;
    private final TokenManager _tokenManager;

    @Inject
    public GetRevision(IncomingStreams iss, Metrics m, OutgoingStreams oss,
            IPhysicalStorage ps, RPC rpc, TokenManager tokenManager)
    {
        _iss = iss;
        _m = m;
        _oss = oss;
        _ps = ps;
        _rpc = rpc;
        _tokenManager = tokenManager;
    }

    public void rpc_(SIndex sidx, Path path, byte[] index, DID did, FileOutputStream os, Token tk)
        throws Exception
    {
        assert false : "convert into relative path";
        PBCore core = CoreUtil.newCall(Type.GET_REVISION_CALL)
            .setGetRevisionCall(
                PBGetRevisionCall.newBuilder()
                    .addAllObjectPathElement(path.asList())
                    .setIndex(ByteString.copyFrom(index))
            ).build();

        DigestedMessage msg = _rpc.do_(did, sidx, core, tk, "gr " + sidx + path);
        processReply_(msg, os, tk);
    }

    public void processCall_(DigestedMessage msg) throws Exception
    {
        Util.checkPB(msg.pb().hasGetRevisionCall(), PBGetRevisionCall.class);
        PBGetRevisionCall pb = msg.pb().getGetRevisionCall();

        byte[] index = pb.getIndex().toByteArray();
        IPhysicalRevProvider provider = _ps.getRevProvider();
        RevInputStream ris = provider.getRevInputStream_(new Path(pb.getObjectPathElementList()), index);

        PBCore core = CoreUtil.newReply(msg.pb())
                .setGetRevisionReply(PBGetRevisionReply.newBuilder()
                        .setLength(ris._length))
                .build();

        InvalidationReason reason = InvalidationReason.INTERNAL_ERROR;

        Token tk = _tokenManager.acquireThrows_(Cat.SERVER, "GRSendReply");
        try {
            OutgoingStream outgoing = _oss.newStream(msg.ep(), msg.sidx(), tk);
            try {
                // I'm lazy and don't want to combine the PB message with first
                // bulk of file content into one chunk.
                outgoing.sendChunk_(Util.writeDelimited(core).toByteArray());

                DataInputStream dis = new DataInputStream(ris._is);
                long remaining = ris._length;
                while (remaining > 0) {
                    byte[] bs = new byte[(int) Math.min(remaining,
                            _m.getMaxUnicastSize_())];
                    dis.readFully(bs);
                    outgoing.sendChunk_(bs);
                    remaining -= bs.length;
                }

                assert remaining == 0;
                reason = null;

            } finally {
                if (reason == null) outgoing.end_(); else outgoing.abort_(reason);
            }
        } finally {
            tk.reclaim_();
        }
    }

    private void processReply_(DigestedMessage msg, FileOutputStream os, Token tk) throws Exception
    {
        if (msg.streamKey() == null) throw new ExProtocolError("expect a stream");

        try {
            Util.checkPB(msg.pb().hasGetRevisionReply(), PBGetRevisionReply.class);
            long remaining = msg.pb().getGetRevisionReply().getLength();

            // we expect nothing after the header
            assert msg.is().available() == 0;

            while (remaining > 0) {
                InputStream is = _iss.recvChunk_(msg.streamKey(), tk);
                remaining -= Util.copy(is, os);
            }

            assert remaining == 0;

        } finally {
            _iss.end_(msg.streamKey());
        }

    }
}
