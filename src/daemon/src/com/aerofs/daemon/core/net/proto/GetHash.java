package com.aerofs.daemon.core.net.proto;

import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.Hasher;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.core.net.NSL;
import com.aerofs.daemon.core.net.OutgoingStreams;
import com.aerofs.daemon.core.net.OutgoingStreams.OutgoingStream;
import com.aerofs.daemon.core.net.RPC;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.ex.ExTimeout;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBGetHashCall;
import com.aerofs.proto.Core.PBGetHashReply;
import com.aerofs.proto.Core.PBGetHashReply.Builder;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;

public class GetHash
{
    private static Logger l = Util.l(GetHash.class);
    private final RPC _rpc;
    private final NSL _nsl;
    private final OutgoingStreams _oss;
    private final IncomingStreams _iss;
    private final Hasher _hasher;
    private final DirectoryService _ds;
    private final NativeVersionControl _nvc;
    private final TokenManager _tokenManager;
    private final Metrics _metrics;

    @Inject
    public GetHash(NativeVersionControl nvc, DirectoryService ds, Hasher hasher, RPC rpc,
            TokenManager tokenManager, NSL nsl, IncomingStreams iss, OutgoingStreams oss,
            Metrics metrics)
    {
        _nvc = nvc;
        _ds = ds;
        _hasher = hasher;
        _rpc = rpc;
        _nsl = nsl;
        _tokenManager = tokenManager;
        _oss = oss;
        _iss = iss;
        _metrics = metrics;
    }

    private ContentHash processReply_(DigestedMessage msg, Token tk)
            throws ExProtocolError, IOException, ExTimeout, ExStreamInvalid,
            ExAborted, ExNoResource
    {
        Util.checkPB(msg.pb().hasGetHashReply(), PBGetHashReply.class);
        // TODO: reassemble?
        int bytesToReceive = msg.pb().getGetHashReply().getHashLength();
        byte[] contentHashBytes = new byte[bytesToReceive];
        int bytesReceived = 0;
        if (msg.streamKey() != null) {
            l.debug("streaming input, expecting " + bytesToReceive + " bytes");
            // Streamed.
            // Fetch a chunk, copy it into contentHashBytes
            while (bytesReceived < bytesToReceive) {
                // Get the next chunk
                ByteArrayInputStream is = _iss.recvChunk_(msg.streamKey(), tk);
                try {
                    int read = 0;
                    while (read != -1) {
                        read = is.read(contentHashBytes, bytesReceived,
                                bytesToReceive - bytesReceived);
                        if (read != -1) {
                            bytesReceived += read;
                            l.debug("Read " + read + " bytes; " + bytesReceived + " of " +
                                    bytesToReceive);
                        }
                    }
                } finally {
                    is.close();
                }
            }
        } else {
            // Everything fit in a datagram, so the data is all in msg.is()
            DataInputStream ds = new DataInputStream(msg.is());
            try {
                ds.readFully(contentHashBytes);
            } finally {
                ds.close();
            }
        }
        return new ContentHash(contentHashBytes);
    }

    public ContentHash rpc_(SOID soid, Version vRemote, DID did, Token tk)
            throws Exception
    {
        PBGetHashCall.Builder bd = PBGetHashCall.newBuilder()
                .setObjectId(soid.oid().toPB())
                .setRemoteVersion(vRemote.toPB_());

        PBCore call = CoreUtil.newCall(Type.GET_HASH_CALL)
            .setGetHashCall(bd).build();

        DigestedMessage msg = _rpc.do_(did, soid.sidx(), call, tk, GetHash.class.getName() + " " +
                soid);

        return processReply_(msg, tk);
    }

    public void sendReply_(DigestedMessage msg, SOCKID k) throws Exception
    {
        ContentHash h;
        Token tk = _tokenManager.acquireThrows_(Cat.SERVER, "GHSendReply");
        try {
            h = _hasher.computeHash_(k.sokid(), true, tk);
            byte[] hashByteArray = h.toPB().toByteArray();

            Builder bdReply = PBGetHashReply.newBuilder()
                                              .setHashLength(hashByteArray.length);
            PBCore core = CoreUtil.newReply(msg.pb())
                                   .setGetHashReply(bdReply)
                                   .build();

            // Check if this reply (hash and all) will fit in a single unicast packet.
            // If so, send as a datagram.  Otherwise, stream the hash.
            // This really should be handled by the network layer...

            ByteArrayOutputStream out = Util.writeDelimited(core);
            out.write(h.getBytes());
            byte[] messageToSend = out.toByteArray();
            int totalByteCount = messageToSend.length;

            if (totalByteCount > _metrics.getMaxUnicastSize_()) {
                // This blob is too big to send via unicast.  Set up a stream.
                l.debug("blob too big for unicast (" + totalByteCount + " > " +
                        _metrics.getMaxUnicastSize_() + ").  Streaming.");
                InvalidationReason reason = InvalidationReason.INTERNAL_ERROR;
                OutgoingStream outgoing = _oss.newStream(msg.ep(), k.sidx(), tk);
                // Send the entire message.
                try {
                    int bytesSent = 0;
                    while (bytesSent < messageToSend.length) {
                        int bytesLeft = messageToSend.length - bytesSent;
                        int thisBufSize = Math.min(bytesLeft, _metrics.getMaxUnicastSize_() );
                        // Unlike InputStream, copyOfRange takes (buffer, from, to),
                        // rather than (buffer, offset, len).  Tricky.
                        byte[] chunk = Arrays.copyOfRange(messageToSend, bytesSent,
                                bytesSent + thisBufSize);
                        l.debug("sent " + bytesSent + " of " + messageToSend.length);
                        outgoing.sendChunk_(chunk);
                        bytesSent += chunk.length;
                    }
                    reason = null; // mark success
                } finally {
                    if (reason == null) {
                        l.debug("completed sending chunks");
                        outgoing.end_();
                    } else {
                        l.debug("aborted!");
                        outgoing.abort_(reason);
                    }
                }
            } else {
                l.debug("blob fit in unicast packet");
                _nsl.sendUnicast_(msg.did(), msg.sidx(), CoreUtil.typeString(core), core.getRpcid(),
                        out);
            }
        } finally {
            tk.reclaim_();
        }
    }

    public void processCall_(DigestedMessage msg) throws Exception
    {
        Util.checkPB(msg.pb().hasGetHashCall(), PBGetHashCall.class);
        PBGetHashCall pb = msg.pb().getGetHashCall();

        SOID soid = new SOID(msg.sidx(), new OID(pb.getObjectId()));

        Version vRemote = new Version(pb.getRemoteVersion());

        for (KIndex kIdx: _ds.getOAThrows_(soid).cas().keySet()) {
            SOCKID k = new SOCKID(soid, CID.CONTENT, kIdx);
            Version vLocal = _nvc.getLocalVersion_(k);

            // There should be only 1 match
            if (vLocal.equals(vRemote)) {
                sendReply_(msg, k);
                return;
            }
        }

        l.debug("No matching version. Throwing NOT_FOUND");
        PBCore core = CoreUtil.newReply(msg.pb())
                .setExceptionReply(Exceptions.toPB(new ExNotFound()))
                .build();
        _nsl.sendUnicast_(msg.did(), msg.sidx(), core);
    }

}
