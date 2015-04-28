package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.phy.DigestSerializer;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.fs.FileChunker;
import com.aerofs.daemon.transport.lib.OutgoingStream;
import com.aerofs.lib.*;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBGetContentRequest;
import com.aerofs.proto.Core.PBGetContentResponse;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

public class ContentSender
{
    private static final Logger l = Loggers.getLogger(ContentSender.class);

    protected final Metrics _m;
    protected final TransportRoutingLayer _trl;
    private final UploadState _ulstate;
    protected final TokenManager _tokenManager;
    private final CoreScheduler _sched;

    private final Set<OngoingTransfer> _ongoing = new HashSet<>();

    @Inject
    public ContentSender(UploadState ulstate, CoreScheduler sched, TransportRoutingLayer trl,
                         Metrics m, TokenManager tokenManager)
    {
        _sched = sched;
        _ulstate = ulstate;

        _trl = trl;
        _m = m;
        _tokenManager = tokenManager;
    }

    public ContentHash send_(
            Endpoint ep, SendableContent content,
            @Nullable PBGetContentRequest.Prefix prefix,
            PBCore.Builder bdCore, PBGetContentResponse.Builder bd) throws Exception
    {
        try {
            return sendInternal_(ep, content, prefix, bdCore, bd);
        } catch (ExUpdateInProgress e) {
            content.pf.onUnexpectedModification_(content.mtime);
            throw e;
        }
    }

    private ContentHash sendInternal_(Endpoint ep, SendableContent content,
                                      @Nullable PBGetContentRequest.Prefix prefix,
                                      PBCore.Builder bdCore,
                                      PBGetContentResponse.Builder bd) throws Exception {
        bd.setMtime(content.mtime);
        bd.setLength(content.length);

        if (content.hash == null) {
            if (Cfg.storageType() == StorageType.LINKED) {
                throw new ExUpdateInProgress("wait for hash to serve content");
            } else {
                // NB: it is theoretically possible that some old block storage TS deployed before
                // incremental content hashing was rolled out would have no hash for some files
                // Since TS will be deprecated in favor of the shiny new Storage Agent when phoenix
                // is rolled out it is deemed tentatively acceptable to break backwards compat
                // and require a content hash in *all* responses.
                // TODO: consider writing a DPUT/DLT to compute missing whole-file hashes on old TS
                throw new ExProtocolError("missing content hash");
            }
        }

        bd.setHash(content.hash.toPB());

        long prefixLen = 0;
        MessageDigest md = null;
        if (prefix != null && prefix.getLength() > 0) {
            try {
                md = DigestSerializer.deserialize(
                        prefix.getHashState().asReadOnlyByteBuffer(), prefix.getLength());
                prefixLen = prefix.getLength();
                bd.setPrefixLength(prefixLen);
            } catch (IllegalArgumentException e) {
                l.warn("{} invalid prefix hash state {}", ep, content.sokid, e);
                throw new ExProtocolError("invalid prefix hash state");
            }
        }

        PBCore response = bdCore.setGetContentResponse(bd).build();
        ByteArrayOutputStream os = Util.writeDelimited(response);
        if (!bd.hasPrefixLength() && os.size() + content.length <= _m.getMaxUnicastSize_()) {
            return sendSmall_(ep, content, os, response);
        } else {
            if (md == null) md = BaseSecUtil.newMessageDigest();
            try (Token tk = _tokenManager.acquireThrows_(Cat.SERVER,
                    "SendContent(" + content.sokid + ", " + ep + ")")) {
                return sendBig_(ep, content, os, prefixLen, tk, md);
            }
        }
    }

    protected ContentHash sendSmall_(Endpoint ep, SendableContent c, ByteArrayOutputStream os, PBCore reply)
            throws Exception
    {
        // the file might not exist if len is 0
        InputStream is = c.length == 0 ? null : c.pf.newInputStream();

        MessageDigest md = SecUtil.newMessageDigest();
        if (is != null) is = new DigestInputStream(is, md);

        try {
            long copied = is != null ? ByteStreams.copy(is, os) : 0;

            if (copied != c.length || c.pf.wasModifiedSince(c.mtime, c.length)) {
                throw new ExUpdateInProgress(c.sokid + " has changed locally: expected=("
                        + c.mtime + "," + c.length + ") actual=("
                        + c.pf.lastModified() + "," + c.pf.lengthOrZeroIfNotFile() + ")");
            }

            _trl.sendUnicast_(ep, CoreProtocolUtil.typeString(reply), reply.getRpcid(), os);
        } finally {
            if (is != null) {
                is.close();
            }
        }
        return new ContentHash(md.digest());
    }

    protected ContentHash sendBig_(Endpoint ep, SendableContent c, ByteArrayOutputStream os,
            long prefixLen, Token tk, @Nullable MessageDigest md)
            throws Exception {
        l.debug("sendBig_: os.size() = {}", os.size());
        checkState(prefixLen >= 0);

        OngoingTransfer ul = new OngoingTransfer(_sched, _ulstate, ep, c.sokid.soid(), c.length);
        checkState(_ongoing.add(ul));
        try {
            TCB tcb = tk.pseudoPause_("snd-" + c.sokid);
            try {
                return sendBig(ep, c, os, prefixLen, ul, md);
            } finally {
                tcb.pseudoResumed_();
            }
        } catch (Exception e) {
            _ulstate.ended_(new SOCID(c.sokid.soid(), CID.CONTENT), ep, true);
            throw e;
        } finally {
            _ongoing.remove(ul);
        }
    }

    // NB: called with core lock released
    private ContentHash sendBig(Endpoint ep, SendableContent c, ByteArrayOutputStream os, long prefixLen,
            OngoingTransfer ongoing, @Nullable MessageDigest md)
            throws Exception {
        final OutgoingStream outgoing = ep.tp().newOutgoingStream(ep.did());
        final FileChunker chunker = new FileChunker(c.pf, c.mtime, c.length, prefixLen,
                _m.getMaxUnicastSize_(), OSUtil.isWindows());

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
                        throw new ExUpdateInProgress(c.sokid + " updated");
                    }
                    ongoing.progress(c.length - done);
                    timer.restart();
                }

                // TODO: an async stream API would allow pipelining disk and network I/O
                outgoing.write(buf);
                if (md != null) md.update(buf);
                done += buf.length;
            }

            checkState(done == c.length);
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
