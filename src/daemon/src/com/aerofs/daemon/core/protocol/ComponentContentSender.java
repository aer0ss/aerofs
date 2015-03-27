package com.aerofs.daemon.core.protocol;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.NativeVersionControl.IVersionControlListener;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.fs.FileChunker;
import com.aerofs.daemon.transport.lib.OutgoingStream;
import com.aerofs.lib.*;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBGetComponentResponse;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkState;

public class ComponentContentSender
{
    private static final Logger l = Loggers.getLogger(ComponentContentSender.class);

    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final Metrics _m;
    private final TransportRoutingLayer _trl;
    private final UploadState _ulstate;
    private final TokenManager _tokenManager;
    private final CoreScheduler _sched;

    private final Map<SOCKID, OngoingTransfer> _ongoing = new ConcurrentHashMap<>();

    @Inject
    public ComponentContentSender(UploadState ulstate, CoreScheduler sched,
            TransportRoutingLayer trl, IPhysicalStorage ps, NativeVersionControl nvc, Metrics m,
            DirectoryService ds, TokenManager tokenManager)
    {
        _sched = sched;
        _ulstate = ulstate;

        _trl = trl;
        _m = m;
        _ds = ds;
        _ps = ps;
        _tokenManager = tokenManager;

        nvc.addListener_(new IVersionControlListener() {
            @Override
            public void localVersionAdded_(SOCKID k, Version v, Trans t) throws SQLException {
                OngoingTransfer ot = _ongoing.remove(k);
                if (ot != null) ot.abort();
            }
        });
    }

    // raw file bytes are appended after BPCore
    ContentHash send_(
            Endpoint ep,
            SOCKID k,
            PBCore.Builder bdCore,
            PBGetComponentResponse.Builder bdResponse,
            Version vLocal,
            long prefixLen, Version vPrefix,
            @Nullable ContentHash remoteHash)
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
        bdResponse.setFileTotalLength(fileLength);
        bdResponse.setMtime(mtime);

        try {
            return send_(ep, k, bdCore, bdResponse, vLocal, prefixLen, vPrefix, remoteHash, pf, fileLength, mtime);
        } catch (ExUpdateInProgress e) {
            pf.onUnexpectedModification_(mtime);
            throw e;
        }
    }

    private ContentHash send_(
            Endpoint ep,
            SOCKID k,
            PBCore.Builder bdCore,
            PBGetComponentResponse.Builder bdResponse,
            Version vLocal,
            long prefixLen,
            Version vPrefix,
            @Nullable ContentHash remoteHash,
            IPhysicalFile pf,
            long fileLength,
            long mtime)
            throws Exception
    {
        // Send hash if available.
        final ContentHash h = _ds.getCAHash_(k.sokid());
        boolean contentIsSame = false;

        if (h != null) {
            if (remoteHash != null && h.equals(remoteHash)) {
                contentIsSame = true;
                bdResponse.setIsContentSame(true);
                l.info("Content same");
            } else {
                l.info("Sending hash: {}", h);
                bdResponse.setHash(h.toPB());
            }
        } else {
            // refuse to serve content until the hash is known
            // NB: for backwards compat reason, this cannot be used for BlockStorage clients
            // deployed before incremental prefix hashing was introduced, as files larger than
            // the block size transferred before that may not have a valid content hash.
            // For linked storage the linker/scanner will eventually compute the hash (assuming
            // it is not modified faster than the hash can be computed but in that case we have
            // no hope of ever transferring it anyway...)
            if (Cfg.storageType() == StorageType.LINKED) {
                throw new ExUpdateInProgress("wait for hash to serve content");
            }
        }

        PBCore response = bdCore.setGetComponentResponse(bdResponse).build();
        ByteArrayOutputStream os = Util.writeDelimited(response);
        if (os.size() <= _m.getMaxUnicastSize_() && contentIsSame) {
            sendContentSame_(ep, os, response);
        } else if (os.size() + fileLength <= _m.getMaxUnicastSize_()) {
            return sendSmall_(ep, k, os, response, mtime, fileLength, pf);
        } else {
            long newPrefixLen = vLocal.equals(vPrefix) ? prefixLen : 0;

            if (prefixLen != 0) {
                l.info("recved prefix len {} v {}. local {} newLen {}",
                        prefixLen, vPrefix, vLocal, newPrefixLen);
            }

            // because the original builders have built, we have
            // to create a new builder
            PBGetComponentResponse.Builder bd = PBGetComponentResponse
                    .newBuilder()
                    .mergeFrom(response.getGetComponentResponse())
                    .setPrefixLength(newPrefixLen);

            os = Util.writeDelimited(PBCore
                    .newBuilder(response)
                    .setGetComponentResponse(bd)
                    .build());

            try (Token tk = _tokenManager.acquireThrows_(Cat.SERVER, "SendContent(" + k + ", " + ep + ")")) {
                return sendBig_(ep, k, os, newPrefixLen, tk, mtime, fileLength, pf);
            }
        }
        return null;
    }

    private void sendContentSame_(Endpoint ep, ByteArrayOutputStream os, PBCore response)
            throws Exception
    {
        _trl.sendUnicast_(ep, CoreProtocolUtil.typeString(response), response.getRpcid(), os);
    }

    private ContentHash sendSmall_(Endpoint ep, SOCKID k, ByteArrayOutputStream os, PBCore reply,
            long mtime, long len, IPhysicalFile pf)
            throws Exception
    {
        // the file might not exist if len is 0
        InputStream is = len == 0 ? null : pf.newInputStream();

        MessageDigest md = SecUtil.newMessageDigest();
        if (is != null) is = new DigestInputStream(is, md);

        try {
            long copied = is != null ? ByteStreams.copy(is, os) : 0;

            if (copied != len || pf.wasModifiedSince(mtime, len)) {
                throw new ExUpdateInProgress(k + " has changed locally: expected=("
                        + mtime + "," + len + ") actual=("
                        + pf.lastModified() + "," + pf.lengthOrZeroIfNotFile() + ")");
            }

            _trl.sendUnicast_(ep, CoreProtocolUtil.typeString(reply), reply.getRpcid(), os);
        } finally {
            if (is != null) {
                is.close();
            }
        }
        return new ContentHash(md.digest());
    }

    private @Nullable MessageDigest createDigestIfNeeded_(long prefixLen, IPhysicalFile pf)
            throws Exception
    {
        // see definition of PREFIX_REHASH_MAX_LENGTH for rationale of rehash limit on prefix length
        if (prefixLen >= DaemonParam.PREFIX_REHASH_MAX_LENGTH) return null;

        MessageDigest md = SecUtil.newMessageDigest();
        if (prefixLen == 0) return md;

        try (InputStream is = pf.newInputStream()) {
            ByteStreams.copy(ByteStreams.limit(is, prefixLen),
                    new DigestOutputStream(ByteStreams.nullOutputStream(), md));
        }
        return md;
    }

    private ContentHash sendBig_(Endpoint ep, SOCKID k, ByteArrayOutputStream os,
            long prefixLen, Token tk, final long mtime, final long len, IPhysicalFile pf)
            throws Exception {
        l.debug("sendBig_: os.size() = {}", os.size());
        checkState(prefixLen >= 0);

        OngoingTransfer ul = new OngoingTransfer(_sched, _ulstate, ep, k.soid(), len);
        checkState(_ongoing.put(k, ul) == null);
        try {
            TCB tcb = tk.pseudoPause_("snd-" + k);
            try {
                return sendBig(ep, k, os, prefixLen, ul, mtime, len, pf);
            } finally {
                tcb.pseudoResumed_();
            }
        } catch (Exception e) {
            _ulstate.ended_(k.socid(), ep, true);
            throw e;
        } finally {
            _ongoing.remove(k);
        }
    }

    // NB: called with core lock released
    private ContentHash sendBig(Endpoint ep, SOCKID k, ByteArrayOutputStream os, long prefixLen,
            OngoingTransfer ongoing, final long mtime, final long len, IPhysicalFile pf)
            throws Exception {
        final OutgoingStream outgoing = ep.tp().newOutgoingStream(ep.did());
        final FileChunker chunker = new FileChunker(pf, mtime, len, prefixLen,
                _m.getMaxUnicastSize_(), OSUtil.isWindows());

        @Nullable MessageDigest md = createDigestIfNeeded_(prefixLen, pf);

        try {
            // First, send the protobuf header
            outgoing.write(os.toByteArray());

            // send the file content, skipping the first prefix-length bytes
            long done = prefixLen;

            ElapsedTimer timer = new ElapsedTimer();

            byte[] buf;
            // When getNextChunk() returns null, there are no more chunks to send
            while ((buf = chunker.getNextChunk_()) != null) {
                // sending notifications is expensive so we use basic rate-limiting
                if (timer.elapsed() > DaemonParam.NOTIFY_THRESHOLD) {
                    if (ongoing.aborted()) {
                        throw new ExUpdateInProgress(k + " updated");
                    }
                    ongoing.progress(len - done);
                    timer.restart();
                }

                // TODO: an async stream API would allow pipelining disk and network I/O
                outgoing.write(buf);
                if (md != null) md.update(buf);
                done += buf.length;
            }

            checkState(done == len);
            ongoing.progress(0);
        } catch (Exception e) {
            l.warn("{} fail send chunk over {} err:{}", ep.did(), ep.tp(), e.getMessage());
            InvalidationReason reason = (e instanceof ExUpdateInProgress) ?
                    InvalidationReason.UPDATE_IN_PROGRESS : InvalidationReason.INTERNAL_ERROR;
            outgoing.abort(reason);
            throw e;
        } finally {
            outgoing.close();
            chunker.close_();
        }

        // TODO: send computed hash after content to detect corruption during transfer?
        return md != null ? new ContentHash(md.digest()) : null;
    }
}
