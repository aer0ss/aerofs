/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.C;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.analytics.AnalyticsEvents.FileConflictEvent;
import com.aerofs.base.analytics.IAnalyticsEvent;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.ex.ExOutOfSpace;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.object.BranchDeleter;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.exception.ExDependsOn;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Version;
import com.aerofs.lib.analytics.AnalyticsEventCounter;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.proto.Core.PBGetComponentResponse;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.sql.SQLException;

import static com.google.common.base.Preconditions.checkState;

public class ReceiveAndApplyUpdate
{
    private static final Logger l = Loggers.getLogger(ReceiveAndApplyUpdate.class);

    private DirectoryService _ds;
    private PrefixVersionControl _pvc;
    private NativeVersionControl _nvc;
    private IPhysicalStorage _ps;
    private DownloadState _dlState;
    private IncomingStreams _iss;
    private BranchDeleter _bd;
    private TransManager _tm;
    private AnalyticsEventCounter _conflictCounter;

    @Inject
    public ReceiveAndApplyUpdate(DirectoryService ds, PrefixVersionControl pvc, NativeVersionControl nvc,
            IPhysicalStorage ps, DownloadState dlState,
            IncomingStreams iss, BranchDeleter bd, TransManager tm,
            Analytics analytics)
    {
        _ds = ds;
        _pvc = pvc;
        _nvc = nvc;
        _ps = ps;
        _dlState = dlState;
        _iss = iss;
        _bd = bd;
        _tm = tm;
        _conflictCounter = new AnalyticsEventCounter(analytics)
        {
            @Override
            public IAnalyticsEvent createEvent(int count)
            {
                return new FileConflictEvent(count);
            }
        };
    }

    private void updatePrefixVersion_(SOCKID k, Version vRemote, boolean isStreaming)
            throws SQLException
    {
        Version vPrefixOld = _pvc.getPrefixVersion_(k.soid(), k.kidx());

        if (isStreaming || !vPrefixOld.isZero_()) {
            Trans t = _tm.begin_();
            try {
                if (!vPrefixOld.isZero_()) {
                    _pvc.deletePrefixVersion_(k.soid(), k.kidx(), t);
                }

                // If we're not streaming data, then the download can't be interrupted.
                // Therefore there is no point to maintain version info for the prefix.
                if (isStreaming) {
                    _pvc.addPrefixVersion_(k.soid(), k.kidx(), vRemote, t);
                }

                t.commit_();

            } finally {
                t.end_();
            }
        }
    }

    private void movePrefixFile_(SOCID socid, KIndex kFrom, KIndex kTo, Version vRemote)
            throws SQLException, IOException
    {
        Trans t = _tm.begin_();
        try {
            // TODO (DF) : figure out if prefix files need a KIndex or are assumed
            // to be MASTER like everything else in Download
            IPhysicalPrefix from = _ps.newPrefix_(new SOCKID(socid, kFrom), null);
            assert from.getLength_() > 0;

            IPhysicalPrefix to = _ps.newPrefix_(new SOCKID(socid, kTo), null);
            from.moveTo_(to, t);

            // note: transaction may fail (i.e. process_ crashes) after the
            // above move and before the commit below, which is fine.

            _pvc.deletePrefixVersion_(socid.soid(), kFrom, t);
            _pvc.addPrefixVersion_(socid.soid(), kTo, vRemote, t);

            t.commit_();
        } finally {
            t.end_();
        }
    }

    /**
     * Opens the prefix file for writing. If there is no prefix length or if the content we are
     * downloading is present locally, then we overwrite the prefix file and update the version
     * of the prefix. Otherwise writing to the returned OutputStream will append to it.
     *
     * NOTE: This method screams for the need to be split up. Look at the name... just look at the
     * name and tell me everything's going to be okay! That's right, lie through your teeth!
     *
     * @param prefix The prefix file to open for writing
     * @param k The SOCKID of the prefix file
     * @param vRemote The version vector of the remote object
     * @param prefixLength The amount of data we already have in the prefix file
     * @param contentPresentLocally Whether the content for the remote object is present locally
     * @param isStreaming Whether we are streaming the content or receiving it in a datagram.
     * @return an OutputStream that writes to the prefix file
     */
    private PrefixDownloadStream createPrefixOutputStreamAndUpdatePrefixState_(IPhysicalPrefix prefix,
            SOCKID k, Version vRemote, long prefixLength,
            boolean contentPresentLocally, boolean isStreaming, Token tk)
            throws ExNotFound, SQLException, IOException, ExAborted
    {
        if (prefixLength == 0 || contentPresentLocally) {
            l.debug("update prefix version");

            // Write the prefix version before we write to the prefix file
            updatePrefixVersion_(k, vRemote, isStreaming);

            // This truncates the file to size zero
            return new PrefixDownloadStream(prefix.newOutputStream_(false));
        }

        if (k.kidx().isMaster()) {
            // if vPrefixOld != vRemote, the prefix version must be updated in the
            // persistent store (see above).
            Version vPrefixOld = _pvc.getPrefixVersion_(k.soid(), k.kidx());

            assert vPrefixOld.equals(vRemote) : Joiner.on(' ').join(vPrefixOld, vRemote, k);

            l.debug("prefix accepted: " + prefixLength);
        } else {
            // kidx of the prefix file has been changed. this may happen if
            // after the last partial download and before the current download,
            // the local peer modified the branch being downloaded, causing a
            // new conflict. we in this case reuse the prefix file for the new
            // branch. the following assertion is because local peers should
            // only be able to change the MASTER branch.
            movePrefixFile_(k.socid(), KIndex.MASTER, k.kidx(), vRemote);

            l.debug("prefix transferred " + KIndex.MASTER + "->" + k.kidx() + ": " + prefixLength);
        }

        checkState(prefix.getLength_() == prefixLength,
                "Prefix length mismatch {} {}", prefixLength, prefix.getLength_());

        // see definition of PREFIX_REHASH_MAX_LENGTH for rationale
        if (prefixLength > DaemonParam.PREFIX_REHASH_MAX_LENGTH) {
            return new PrefixDownloadStream(prefix.newOutputStream_(true), new NullDigest());
        }

        MessageDigest md = SecUtil.newMessageDigest();
        if (prefixLength > 0) hashPrefix_(prefix, prefixLength, md, tk);

        return new PrefixDownloadStream(prefix.newOutputStream_(true), md);
    }

    private void hashPrefix_(IPhysicalPrefix prefix, long prefixLength, MessageDigest md, Token tk)
            throws IOException, ExAborted
    {
        // do not release core lock if the prefix is small
        TCB tcb = prefixLength > 4 * C.KB ? tk.pseudoPause_("rehash " + prefixLength) : null;
        try {
            // re-hash prefix. this is not great because it stalls the download
            // an alternative would be to hash the prefix in a different thread
            // but the increased complexity make this option unattractive.
            // If we trusted the filesystem and had a way to (de)serialize the
            // internal state of the SHA256 computation we could avoid the
            // redundant computation altogether. For now this will have to do.
            try (InputStream is = prefix.newInputStream_()) {
                ByteStreams.copy(is, new DigestOutputStream(ByteStreams.nullOutputStream(), md));
            }
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
        }
    }

    private @Nullable ContentHash writeContentToPrefixFile_(IPhysicalPrefix prefix, DigestedMessage msg,
            final long totalFileLength, final long prefixLength, SOCKID k,
            Version vRemote, @Nullable KIndex matchingLocalBranch, Token tk)
            throws ExOutOfSpace, ExNotFound, ExStreamInvalid, ExAborted, ExNoResource, ExTimeout,
            SQLException, IOException, DigestException
    {
        final boolean isStreaming = msg.streamKey() != null;

        // Open the prefix file for writing, updating it as required
        final PrefixDownloadStream prefixStream = createPrefixOutputStreamAndUpdatePrefixState_(
                prefix, k, vRemote, prefixLength, matchingLocalBranch != null, isStreaming, tk);

        try {
            if (matchingLocalBranch != null && isStreaming) {
                // We have this file locally and we are receiving the remote content via a stream.
                // We can cancel the stream here and use the local content.
                l.debug("reading content from local branch");

                // Stop the stream since we will not be reading it from now on
                _iss.end_(msg.streamKey());

                // Use the content from the local branch
                IPhysicalFile file = _ps.newFile_(_ds.resolve_(k.soid()),
                        matchingLocalBranch);

                try {
                    try (InputStream is = file.newInputStream_()) {
                        // release core lock to avoid blocking while copying a large prefix
                        TCB tcb = tk.pseudoPause_("cp-prefix");
                        try {
                            ByteStreams.copy(is, prefixStream);
                        } finally {
                            tcb.pseudoResumed_();
                        }
                    }
                } catch (FileNotFoundException e) {
                    SOCKID conflict = new SOCKID(k.socid(), matchingLocalBranch);

                    if (!matchingLocalBranch.equals(KIndex.MASTER)) {
                        // A conflict branch does not exist, even though there is an entry
                        // in our database. This is an inconsistency, we must remove the
                        // entry in our database. AeroFS will redownload the conflict at a later
                        // point.
                        l.error("known conflict branch has no associated file: {}", conflict);

                        Trans t = _tm.begin_();
                        try {
                            _bd.deleteBranch_(conflict, _nvc.getLocalVersion_(conflict), false, t);
                            t.commit_();
                        } finally {
                            t.end_();
                        }
                    }

                    l.warn("can't copy content from local branch {}", conflict);

                    throw new ExAborted(e.getMessage());
                }

            } else {
                // We do not have the content locally, or we do but we are receiving the update via
                // a datagram. The datagram is fully received at this point so there is no need to
                // read from the file system the same content we already have in memory.
                l.debug("reading content from network");

                // assert after opening the stream otherwise the file length may
                // have changed after the assertion and before newOutputStream()
                checkState(prefix.getLength_() == prefixLength,
                        "%s %s != %s", k, prefix.getLength_(), prefixLength);

                ElapsedTimer timer = new ElapsedTimer();

                // Read from the incoming message/stream
                InputStream is = msg.is();
                long copied = ByteStreams.copy(is, prefixStream);

                if (isStreaming) {
                    // it's a stream
                    long remaining = totalFileLength - copied - prefixLength;
                    while (remaining > 0) {
                        // sending notifications is not cheap, hence the rate-limiting
                        if (timer.elapsed() > DaemonParam.NOTIFY_THRESHOLD) {
                            OA oa = _ds.getOANullable_(k.soid());
                            if (oa == null || oa.isExpelled()) {
                                prefixStream.close();
                                prefix.delete_();
                                throw new ExAborted("expelled " + k);
                            }
                            _dlState.progress_(k.socid(), msg.ep(), totalFileLength - remaining,
                                    totalFileLength);
                            timer.restart();
                        }
                        is = _iss.recvChunk_(msg.streamKey(), tk);
                        remaining -= ByteStreams.copy(is, prefixStream);
                    }
                    checkState(remaining == 0, "%s %s %s", k, msg.ep(), remaining);
                }
            }
        } finally {
            prefixStream.close();
        }
        return prefixStream.digest();
    }

    public Trans applyContent_(DigestedMessage msg, SOCKID k, Version vRemote,
            @Nullable ContentHash remoteHash, @Nullable KIndex localBranchWithMatchingContent, Token tk)
            throws SQLException, IOException, ExDependsOn, ExTimeout, ExAborted, ExStreamInvalid,
            ExNoResource, ExOutOfSpace, ExNotFound, DigestException
    {
        PBGetComponentResponse response = msg.pb().getGetComponentResponse();

        // Should aliased oid be checked?
        // Since there is no content associated with aliased oid
        // there shouldn't be invocation for applyContent_()?

        // TODO reserve space first

        final IPhysicalPrefix prefix = _ps.newPrefix_(k, null);

        // Write the new content to the prefix file
        // TODO: ideally we'd release the core lock around this entire call
        @Nullable ContentHash h = writeContentToPrefixFile_(prefix, msg, response.getFileTotalLength(),
                response.getPrefixLength(), k, vRemote, localBranchWithMatchingContent, tk);

        if (remoteHash != null && h != null && !h.equals(remoteHash)) {
            l.info("hash mismatch: {} {}", remoteHash, h);
            // hash mismatch can be caused by data corruption on either end of the transfer or
            // inside the transport. Whatever the case may be, we simply can't commit the change.
            // Discard tainted prefix
            prefix.truncate_(0);
            // TODO: more specific exception
            throw new ExAborted("hash mismatch");
        }

        // TODO: pipelined chunking in BlockStorage to get rid of this step
        prefix.prepare_(tk);

        // get length of the prefix before the actual transaction.
        long len = prefix.getLength_();
        boolean okay = false;
        Trans t = _tm.begin_();
        try {
            // can't use the old values as the attributes might have changed
            // during pauses, due to aliasing and such
            OA oa = _ds.getOAThrows_(k.soid());
            ResolvedPath path = _ds.resolve_(oa);
            IPhysicalFile pf = _ps.newFile_(path, k.kidx());

            // abort if the object is expelled. Although Download.java checks
            // for this condition before starting the download, but the object
            // may be expelled during pauses of the current thread.
            if (oa.isExpelled()) {
                prefix.delete_();
                throw new ExAborted("expelled " + k);
            }

            CA ca = oa.caNullable(k.kidx());
            boolean wasPresent = ca != null;
            if (wasPresent && pf.wasModifiedSince(ca.mtime(), ca.length())) {
                // the linked file modified via the local filesystem
                // (i.e. the linker), but the linker hasn't received
                // the notification yet. we should not overwrite the
                // file in this case otherwise the local update will get
                // lost.
                //
                // BUGBUG NB there is still a tiny time window between the
                // test above and the apply_() below that the file is
                // updated via the filesystem.
                pf.onUnexpectedModification_(ca.mtime());
                throw new ExAborted(k + " has changed locally: expected=("
                        + ca.mtime() + "," + ca.length() + ") actual=("
                        + pf.getLastModificationOrCurrentTime_() + "," + pf.getLength_() + ")");
            }

            assert response.hasMtime();
            long replyMTime = response.getMtime();
            assert replyMTime >= 0 : Joiner.on(' ').join(replyMTime, k, vRemote, wasPresent);
            long mtime = _ps.apply_(prefix, pf, wasPresent, replyMTime, t);

            assert msg.streamKey() == null ||
                _pvc.getPrefixVersion_(k.soid(), k.kidx()).equals(vRemote);

            _pvc.deletePrefixVersion_(k.soid(), k.kidx(), t);

            if (!wasPresent) {
                if (!k.kidx().equals(KIndex.MASTER)) {
                    // record creation of conflict branch here instead of in DirectoryService
                    // Aliasing and Migration may recreate them and we only want to record each
                    // conflict once
                    _conflictCounter.inc();
                }
                _ds.createCA_(k.soid(), k.kidx(), t);
            }
            _ds.setCA_(k.sokid(), len, mtime, h, t);

            okay = true;
            return t;

        } finally {
            if (!okay) t.end_();
        }
    }

}
