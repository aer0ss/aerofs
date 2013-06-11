package com.aerofs.daemon.core.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Arrays;

import com.aerofs.base.Loggers;
import com.google.common.base.Joiner;
import org.slf4j.Logger;

import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.core.net.NSL;
import com.aerofs.daemon.core.net.OutgoingStreams;
import com.aerofs.daemon.core.net.OutgoingStreams.OutgoingStream;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.base.id.DID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBGetComReply;
import com.aerofs.proto.Core.PBGetComReply.Builder;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.inject.Inject;

import javax.annotation.Nullable;

/**
 * GCC: GetComponentCall
 */
public class GCCSendContent
{
    private static final Logger l = Loggers.getLogger(GCCSendContent.class);

    private final DirectoryService _ds;
    private final Metrics _m;
    private final NativeVersionControl _nvc;
    private final NSL _nsl;
    private final OutgoingStreams _oss;
    private final UploadState _ulstate;
    private final TokenManager _tokenManager;

    @Inject
    public GCCSendContent(UploadState ulstate, OutgoingStreams oss, NSL nsl,
            NativeVersionControl nvc, Metrics m, DirectoryService ds, TokenManager tokenManager)
    {
        _ulstate = ulstate;
        _oss = oss;
        _nsl = nsl;
        _nvc = nvc;
        _m = m;
        _ds = ds;
        _tokenManager = tokenManager;
    }

    // raw file bytes are appended after BPCore
    void send_(Endpoint ep, SOCKID k, PBCore.Builder bdCore, Builder bdReply, Version vLocal,
            long prefixLen, Version vPrefix, @Nullable ContentHash remoteHash, Version vRemote)
            throws Exception
    {
        // guaranteed by the caller
        assert _ds.isPresent_(k);
        OA oa = _ds.getOA_(k.soid());
        CA ca = oa.ca(k.kidx());
        long mtime = ca.mtime();
        // N.B. this is the length of the complete file contents, regardless of whether we're
        // skipping prefixLen bytes at the beginning of the content or not.
        long fileLength = ca.length();
        IPhysicalFile pf = ca.physicalFile();

        assert mtime >= 0 : Joiner.on(' ').join(k, oa, mtime);
        bdReply.setMtime(mtime);

        // Send hash if available.
        int hashLength = 0;
        ContentHash h = _ds.getCAHash_(k.sokid());
        boolean contentIsSame = false;
        if (h == null) {
            if (!vRemote.sub_(vLocal).isZero_()) {
                // TODO: automatically compute missing hash if requested version does not dominate
                // advertised remote version?
                // This would avoid having to interrupt the transfer on receiver side to request the
                // hash, saving disk bandwidth, network bandwidth, two round trips and cpu cycles...
            }
        }
        if (h != null) {
            if (remoteHash != null && h.equals(remoteHash)) {
                contentIsSame = true;
                bdReply.setIsContentSame(true);
                l.info("Content same");
            } else {
                hashLength = h.toPB().size();
                l.debug("Sending hash length: {}", hashLength);
                bdReply.setHashLength(hashLength);
            }
        }

        PBCore core = bdCore.setGetComReply(bdReply).build();
        ByteArrayOutputStream os = Util.writeDelimited(core);
        if (os.size() <= _m.getMaxUnicastSize_() && contentIsSame) {
            sendContentSame_(ep.did(), os, core);
        } else if (os.size() + hashLength + fileLength <= _m.getMaxUnicastSize_()) {
            sendSmall_(ep.did(), k, os, core, vLocal, mtime, fileLength, h, pf);
        } else {
            long newPrefixLen = vLocal.equals(vPrefix) ? prefixLen : 0;

            l.debug("recved prefix len " + prefixLen + " v "+ vPrefix + ". local " + vLocal +
                    " newLen " + newPrefixLen);

            // because the original builders have built, we have
            // to create a new builder
            Builder bd = PBGetComReply.newBuilder()
                    .mergeFrom(core.getGetComReply())
                    .setFileTotalLength(fileLength)
                    .setPrefixLength(newPrefixLen);
            if (h != null) {
                bd = bd.setHashLength(hashLength);
            }

            os = Util.writeDelimited(PBCore.newBuilder(core)
                    .setGetComReply(bd).build());

            Token tk = _tokenManager.acquireThrows_(Cat.SERVER, "GCRSendBig");
            try {
                sendBig_(ep, k, os, vLocal, newPrefixLen, tk, mtime, fileLength, h, pf);
            } finally {
                tk.reclaim_();
            }
        }
    }

    private boolean wasContentModifiedSince_(SOCKID k, Version v, long mtime, long len,
            IPhysicalFile pf) throws SQLException, IOException, ExNotFound
    {
        return pf.wasModifiedSince(mtime, len) || !_nvc.getLocalVersion_(k).sub_(v).isZero_();
    }

    private int copyAChunk(ByteArrayOutputStream to, InputStream is)
        throws IOException
    {
        // set the buffer size same as chunk size so that we can finish in one
        // read if possible
        byte[] buf = new byte[_m.getMaxUnicastSize_()];
        int total = 0;
        while (total < buf.length) {
            int read = is.read(buf, total, buf.length - total);
            if (read == -1) break;
            total += read;
        }
        to.write(buf, 0, total);
        return total;
    }

    private void sendContentSame_(DID did, ByteArrayOutputStream os, PBCore reply)
            throws Exception
    {
        _nsl.sendUnicast_(did, CoreUtil.typeString(reply), reply.getRpcid(), os);
    }

    private void sendSmall_(DID did, SOCKID k, ByteArrayOutputStream os, PBCore reply, Version v,
            long mtime, long len, ContentHash hash, IPhysicalFile pf)
            throws Exception
    {
        if (hash != null) {
            os.write(hash.toPB().toByteArray());
        }
        // the file might not exist if len is 0
        InputStream is = len == 0 ? null : pf.newInputStream_();

        try {

            long copied;
            if (is != null) {
                copied = copyAChunk(os, is);
            } else {
                copied = 0;
            }

            if (copied != len || wasContentModifiedSince_(k, v, mtime, len, pf)) {
                l.debug(k + " updated while being sent. nak");
                throw new ExUpdateInProgress();
            }

            _nsl.sendUnicast_(did, CoreUtil.typeString(reply), reply.getRpcid(), os);
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    private void sendBig_(Endpoint ep, SOCKID k, ByteArrayOutputStream os, Version v,
            long prefixLen, Token tk, long mtime, long len, ContentHash hash, IPhysicalFile pf)
            throws Exception
    {
        l.debug("sendBig_: os.size() = " + os.size());
        assert prefixLen >= 0;

        InvalidationReason reason = InvalidationReason.INTERNAL_ERROR;
        OutgoingStream outgoing = _oss.newStream(ep, tk);

        InputStream is = null;
        try {
            // First, send the protobuf header
            outgoing.sendChunk_(os.toByteArray());
            os = null; // Because later, we'll send the contents of os again if we don't

            // Second, send the ContentHash (which is separate from the protobuf because it can be
            // too large to fit in a DTLS packet for very large files)
            if (hash != null) {
                byte[] hashByteArray = hash.toPB().toByteArray();
                int chunkBegin = 0;
                int chunkEnd = Math.min(hashByteArray.length, chunkBegin+_m.getMaxUnicastSize_());
                while(chunkBegin < hashByteArray.length) {
                    outgoing.sendChunk_(Arrays.copyOfRange(hashByteArray, chunkBegin, chunkEnd));
                    chunkBegin = chunkEnd;
                    chunkEnd = Math.min(hashByteArray.length, chunkBegin+_m.getMaxUnicastSize_());
                }
            }
            // Third, send the file content, skipping the first prefix-length bytes
            long rest = len - prefixLen;
            long readPosition = prefixLen;
            // Once rest == 0, the receiver has been sent the full content
            while (rest > 0) {
                _ulstate.progress_(k.socid(), ep, len - rest, len);
                long bytesCopied;

                if (os == null) {
                    os = new ByteArrayOutputStream(_m.getMaxUnicastSize_());
                }

                if (is == null) {
                    is = pf.newInputStream_();
                    if (is.skip(readPosition) != readPosition) {
                        throw new IOException("skip() fell short");
                    }
                }

                // TODO avoid calling writeAChunk() for chunks other
                // than the first, as this method introduces redundant
                // copying
                bytesCopied = copyAChunk(os, is);
                readPosition += bytesCopied;

                // sendOutgoingStreamChunk_() call is blocking and on Windows if a large
                // file is transferred it remains locked. So close the
                // file before calling the blocking
                // sendOutgoingStreamChunk_() function.
                if (OSUtil.isWindows()) {
                    is.close();
                    is = null;
                }

                rest -= bytesCopied;
                if (rest < 0 || wasContentModifiedSince_(k, v, mtime, len, pf)) {
                    reason = InvalidationReason.UPDATE_IN_PROGRESS;
                    throw new IOException(k + " updated");
                }

                outgoing.sendChunk_(os.toByteArray());

                os = null;
            }

            assert rest == 0;
            // The following assertion is overzealous - it is possible that after we read the last
            // file chunk from the PhysicalFile and check that the file hasn't changed, but before
            // we finish uploading the last chunk, the file will change or be appended to.  In this
            // case, is.available() will be greater than 0, but the file will have been uploaded
            // correctly in its entirety, and we'll pick up the new version once the notifier
            // catches up.
            //assert OSUtil.isWindows() || is == null || is.available() == 0;
            reason = null;
        } finally {
            if (is != null) is.close();
            if (reason == null) {
                outgoing.end_();
                _ulstate.progress_(k.socid(), ep, len, len);
            } else {
                outgoing.abort_(reason);
                _ulstate.ended_(k.socid(), ep, true);
            }
        }
    }
}
