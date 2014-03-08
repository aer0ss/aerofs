package com.aerofs.daemon.core.protocol;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.NativeVersionControl.IVersionControlListener;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.net.OutgoingStreams;
import com.aerofs.daemon.core.net.OutgoingStreams.OutgoingStream;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBGetComReply;
import com.aerofs.proto.Core.PBGetComReply.Builder;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * GCC: GetComponentCall
 */
public class GCCSendContent
{
    private static final Logger l = Loggers.getLogger(GCCSendContent.class);

    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final Metrics _m;
    private final TransportRoutingLayer _trl;
    private final OutgoingStreams _oss;
    private final UploadState _ulstate;
    private final TokenManager _tokenManager;

    /**
     * Keep track of ongoing upload in a way that allows them to be aborted
     * upon local change without requiring polling db and file metadata before
     * sending each chunk.
     */
    private static class Ongoing
    {
        private final Map<SOCKID, Set<Object>> _state = Maps.newHashMap();

        Object start(SOCKID k)
        {
            Set<Object> s = _state.get(k);
            if (s == null) {
                s = Sets.newHashSet();
                _state.put(k, s);
            }
            Object o = new Object();
            s.add(o);
            return o;
        }

        void stop(SOCKID k, Object o)
        {
            _state.get(k).remove(o);
        }

        void abort(SOCKID k)
        {
            Set<Object> s = _state.get(k);
            if (s != null) s.clear();
        }

        boolean isAborted(SOCKID k, Object o)
        {
            return !_state.get(k).contains(o);
        }
    }

    private final Ongoing _ongoing = new Ongoing();

    @Inject
    public GCCSendContent(UploadState ulstate, OutgoingStreams oss, TransportRoutingLayer trl, IPhysicalStorage ps,
            NativeVersionControl nvc, Metrics m, DirectoryService ds, TokenManager tokenManager)
    {
        _ulstate = ulstate;
        _oss = oss;
        _trl = trl;
        _m = m;
        _ds = ds;
        _ps = ps;
        _tokenManager = tokenManager;

        nvc.addListener_(new IVersionControlListener() {
            @Override
            public void localVersionAdded_(SOCKID k, Version v, Trans t)
                    throws SQLException
            {
                _ongoing.abort(k);
            }
        });
    }

    // raw file bytes are appended after BPCore
    void send_(Endpoint ep, SOCKID k, PBCore.Builder bdCore, Builder bdReply, Version vLocal,
            long prefixLen, Version vPrefix, @Nullable ContentHash remoteHash, Version vRemote)
            throws Exception
    {
        // guaranteed by the caller
        assert _ds.isPresent_(k);
        OA oa = _ds.getOA_(k.soid());
        CA ca = oa.caThrows(k.kidx());
        long mtime = ca.mtime();
        // N.B. this is the length of the complete file contents, regardless of whether we're
        // skipping prefixLen bytes at the beginning of the content or not.
        long fileLength = ca.length();
        IPhysicalFile pf = _ps.newFile_(_ds.resolve_(oa), k.kidx());

        assert mtime >= 0 : Joiner.on(' ').join(k, oa, mtime);
        bdReply.setMtime(mtime);

        try {
            send_(ep, k, bdCore, bdReply, vLocal, prefixLen, vPrefix, remoteHash, vRemote, pf,
                    fileLength, mtime);
        } catch (ExUpdateInProgress e) {
            pf.onUnexpectedModification_(mtime);
            throw e;
        }
    }

    private void send_(Endpoint ep, SOCKID k, PBCore.Builder bdCore, Builder bdReply,
            Version vLocal, long prefixLen, Version vPrefix, @Nullable ContentHash remoteHash,
            Version vRemote, IPhysicalFile pf, long fileLength, long mtime)
            throws Exception
    {
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
            sendSmall_(ep.did(), k, os, core, mtime, fileLength, h, pf);
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
                sendBig_(ep, k, os, newPrefixLen, tk, mtime, fileLength, h, pf);
            } finally {
                tk.reclaim_();
            }
        }
    }

    private void sendContentSame_(DID did, ByteArrayOutputStream os, PBCore reply)
            throws Exception
    {
        _trl.sendUnicast_(did, CoreUtil.typeString(reply), reply.getRpcid(), os);
    }

    private void sendSmall_(DID did, SOCKID k, ByteArrayOutputStream os, PBCore reply,
            long mtime, long len, ContentHash hash, IPhysicalFile pf)
            throws Exception
    {
        if (hash != null) {
            os.write(hash.toPB().toByteArray());
        }
        // the file might not exist if len is 0
        InputStream is = len == 0 ? null : pf.newInputStream_();

        try {
            long copied = is != null ? ByteStreams.copy(is, os) : 0;

            if (copied != len || pf.wasModifiedSince(mtime, len)) {
                throw new ExUpdateInProgress(k + " has changed locally: expected=("
                        + mtime + "," + len + ") actual=("
                        + pf.getLastModificationOrCurrentTime_() + "," + pf.getLength_() + ")");
            }

            _trl.sendUnicast_(did, CoreUtil.typeString(reply), reply.getRpcid(), os);
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    // TODO: ideally that whole method could run wo/ core lock being held
    // depends on: network refactor, rework upload progress notifications
    private void sendBig_(Endpoint ep, SOCKID k, ByteArrayOutputStream os,
            long prefixLen, Token tk, final long mtime, final long len,
            ContentHash hash, IPhysicalFile pf)
            throws Exception
    {
        l.debug("sendBig_: os.size() = " + os.size());
        checkState(prefixLen >= 0);

        final OutgoingStream outgoing = _oss.newStream(ep, tk);
        final FileChunker chunker = new FileChunker(pf, mtime, len, prefixLen,
                _m.getMaxUnicastSize_(), OSUtil.isWindows());

        Object ongoing = _ongoing.start(k);
        try {
            // First, send the protobuf header
            outgoing.sendChunk_(os.toByteArray());

            // Second, send the ContentHash (which is separate from the protobuf because it can be
            // too large to fit in a unicast packet for very large files)
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
            long done = prefixLen;

            ElapsedTimer timer = new ElapsedTimer();

            byte[] buf;
            // When getNextChunk() returns null, there are no more chunks to send
            while ((buf = chunker.getNextChunk_()) != null) {

                // sending notifications is expensive so we use basic rate-limiting
                if (timer.elapsed() > DaemonParam.NOTIFY_THRESHOLD) {
                    _ulstate.progress_(k.socid(), ep, done, len);
                    timer.restart();
                }

                if (_ongoing.isAborted(k, ongoing)) {
                    throw new ExUpdateInProgress(k + " updated");
                }

                // TODO: an async stream API would allow pipelining disk and network I/O
                outgoing.sendChunk_(buf);
                done += buf.length;
            }

            checkState(done == len);
            outgoing.end_();
            _ulstate.progress_(k.socid(), ep, len, len);

        } catch (Exception e) {
            InvalidationReason reason = (e instanceof ExUpdateInProgress) ?
                    InvalidationReason.UPDATE_IN_PROGRESS : InvalidationReason.INTERNAL_ERROR;
            outgoing.abort_(reason);
            _ulstate.ended_(k.socid(), ep, true);
        } finally {
            _ongoing.stop(k, ongoing);
            chunker.close_();
        }
    }
}
