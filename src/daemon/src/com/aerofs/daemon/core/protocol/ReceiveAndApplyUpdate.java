/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.analytics.AnalyticsEvents.FileConflictEvent;
import com.aerofs.base.analytics.IAnalyticsEvent;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
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
import com.aerofs.daemon.core.phy.PrefixOutputStream;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.exception.ExDependsOn;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Version;
import com.aerofs.lib.analytics.AnalyticsEventCounter;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.proto.Core.PBGetComponentResponse;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestException;
import java.sql.SQLException;

import static com.aerofs.defects.Defects.newMetric;
import static com.google.common.base.Preconditions.checkState;

public class ReceiveAndApplyUpdate
{
    private static final Logger l = Loggers.getLogger(ReceiveAndApplyUpdate.class);

    private final DirectoryService _ds;
    private final PrefixVersionControl _pvc;
    private final NativeVersionControl _nvc;
    private final IPhysicalStorage _ps;
    private final DownloadState _dlState;
    private final IncomingStreams _iss;
    private final BranchDeleter _bd;
    private final TransManager _tm;
    private final AnalyticsEventCounter _conflictCounter;
    private final ChangeEpochDatabase _cedb;

    @Inject
    public ReceiveAndApplyUpdate(DirectoryService ds, PrefixVersionControl pvc, NativeVersionControl nvc,
            IPhysicalStorage ps, DownloadState dlState, ChangeEpochDatabase cedb,
            IncomingStreams iss, BranchDeleter bd, TransManager tm, Analytics analytics)
    {
        _ds = ds;
        _pvc = pvc;
        _nvc = nvc;
        _ps = ps;
        _dlState = dlState;
        _iss = iss;
        _bd = bd;
        _tm = tm;
        _cedb = cedb;
        _conflictCounter = new AnalyticsEventCounter(analytics)
        {
            @Override
            public IAnalyticsEvent createEvent(int count)
            {
                return new FileConflictEvent(count);
            }
        };
    }

    private void updatePrefixVersion_(SOKID k, Version vRemote, boolean isStreaming)
            throws SQLException
    {
        Version vPrefixOld = _pvc.getPrefixVersion_(k.soid(), k.kidx());

        if (isStreaming || !vPrefixOld.isZero_()) {
            try (Trans t = _tm.begin_()) {
                if (!vPrefixOld.isZero_()) {
                    _pvc.deletePrefixVersion_(k.soid(), k.kidx(), t);
                }

                // If we're not streaming data, then the download can't be interrupted.
                // Therefore there is no point to maintain version info for the prefix.
                if (isStreaming) {
                    _pvc.addPrefixVersion_(k.soid(), k.kidx(), vRemote, t);
                }

                t.commit_();
            }
        }
    }

    private void movePrefixFile_(SOID soid, KIndex kFrom, KIndex kTo, Version vRemote)
            throws SQLException, IOException
    {
        try (Trans t = _tm.begin_()) {
            // TODO (DF) : figure out if prefix files need a KIndex or are assumed
            // to be MASTER like everything else in Download
            IPhysicalPrefix from = _ps.newPrefix_(new SOKID(soid, kFrom), null);
            assert from.getLength_() > 0;

            IPhysicalPrefix to = _ps.newPrefix_(new SOKID(soid, kTo), null);
            from.moveTo_(to, t);

            // note: transaction may fail (i.e. process_ crashes) after the
            // above move and before the commit below, which is fine.

            _pvc.deletePrefixVersion_(soid, kFrom, t);
            _pvc.addPrefixVersion_(soid, kTo, vRemote, t);

            t.commit_();
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
    private PrefixOutputStream createPrefixOutputStreamAndUpdatePrefixState_(IPhysicalPrefix prefix,
            SOKID k, Version vRemote, long prefixLength,
            boolean contentPresentLocally, boolean isStreaming)
            throws ExNotFound, SQLException, IOException, ExAborted
    {
        if (prefixLength == 0 || contentPresentLocally) {
            l.debug("update prefix version");

            // Write the prefix version before we write to the prefix file
            updatePrefixVersion_(k, vRemote, isStreaming);

            // This truncates the file to size zero
            return prefix.newOutputStream_(false);
        }

        if (k.kidx().isMaster()) {
            // if vPrefixOld != vRemote, the prefix version must be updated in the
            // persistent store (see above).
            Version vPrefixOld = _pvc.getPrefixVersion_(k.soid(), k.kidx());

            assert vPrefixOld.equals(vRemote) : Joiner.on(' ').join(vPrefixOld, vRemote, k);

            l.debug("prefix accepted: {}", prefixLength);
        } else {
            // kidx of the prefix file has been changed. this may happen if
            // after the last partial download and before the current download,
            // the local peer modified the branch being downloaded, causing a
            // new conflict. we in this case reuse the prefix file for the new
            // branch. the following assertion is because local peers should
            // only be able to change the MASTER branch.
            movePrefixFile_(k.soid(), KIndex.MASTER, k.kidx(), vRemote);

            l.debug("prefix transferred {}->{}: {}", KIndex.MASTER, k.kidx(), prefixLength);
        }

        checkState(prefix.getLength_() == prefixLength,
                "Prefix length mismatch %s %s", prefixLength, prefix.getLength_());

        return prefix.newOutputStream_(true);
    }

    private @Nonnull ContentHash writeContentToPrefixFile_(IPhysicalPrefix prefix, DigestedMessage msg,
            final long totalFileLength, final long prefixLength, SOKID k,
            Version vRemote, @Nullable KIndex matchingLocalBranch, Token tk)
            throws ExOutOfSpace, ExNotFound, ExStreamInvalid, ExAborted, ExNoResource, ExTimeout,
            SQLException, IOException, DigestException
    {
        final boolean isStreaming = msg.streamKey() != null;

        // Open the prefix file for writing, updating it as required
        final PrefixOutputStream prefixStream = createPrefixOutputStreamAndUpdatePrefixState_(
                prefix, k, vRemote, prefixLength, matchingLocalBranch != null, isStreaming);

        // PrefixOutputStream relies on close() being called to preserve the SHA256 internal
        // state. If the internal state is not saved properly, further attempt to reopen the
        // prefix will fail as it will be considered corrupted.
        // Unfortunately the UI will sometimes stop the daemon, for instance when installing
        // an update or relocating the root anchor. To avoid losing wasting progress made on
        // ongoing downloads we use a shutdown hook to maximize the likelihood of the prefix
        // stream being cleanly closed in such circumstances.
        Thread prefixCloser = new Thread(() -> {
            try { prefixStream.close(); } catch (IOException e) {}
        });
        Runtime.getRuntime().addShutdownHook(prefixCloser);

        try {
            if (matchingLocalBranch != null && isStreaming) {
                // We have this file locally and we are receiving the remote content via a stream.
                // We can cancel the stream here and use the local content.
                l.debug("reading content from local branch");

                // Stop the stream since we will not be reading it from now on
                _iss.end_(msg.streamKey());

                // Use the content from the local branch
                IPhysicalFile file = _ps.newFile_(_ds.resolve_(k.soid()), matchingLocalBranch);

                try {
                    TCB tcb = tk.pseudoPause_("cp-prefix");
                    try (InputStream is = file.newInputStream()) {
                        // release core lock to avoid blocking while copying a large prefix
                        ByteStreams.copy(is, prefixStream);
                    } finally {
                        tcb.pseudoResumed_();
                    }
                } catch (FileNotFoundException e) {
                    SOCKID conflict = new SOCKID(k.soid(), CID.CONTENT, matchingLocalBranch);

                    if (!matchingLocalBranch.isMaster()) {
                        // A conflict branch does not exist, even though there is an entry
                        // in our database. This is an inconsistency, we must remove the
                        // entry in our database. AeroFS will redownload the conflict at a later
                        // point.
                        l.error("known conflict branch has no associated file: {}", conflict);

                        try (Trans t = _tm.begin_()) {
                            if (_cedb.getChangeEpoch_(k.sidx()) != null) {
                                // TODO(phoenix): version adjustment?
                                //   rewind -> reset central version to base version of MASTER ca
                                //   merge  -> update base version of MASTER CA
                                // NB: currently, not doing anything is equivalent to a merge
                                _ds.deleteCA_(k.soid(), matchingLocalBranch, t);
                            } else {
                                _bd.deleteBranch_(conflict, _nvc.getLocalVersion_(conflict), false,
                                        t);
                            }
                            t.commit_();
                        }
                    }

                    l.warn("can't copy content from local branch {}", conflict);

                    throw new ExAborted(e.getMessage());
                }
            } else {
                // We do not have the content locally, or we do but we are receiving the update via
                // a datagram. The datagram is fully received at this point so there is no need to
                // read from the file system the same content we already have in memory.
                l.info("{} {} dl {} bytes", msg.did(), k, totalFileLength - prefixLength);

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
                    SOCID socid = new SOCID(k.soid(), CID.CONTENT);
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
                            _dlState.progress_(socid, msg.ep(), totalFileLength - remaining,
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
            Runtime.getRuntime().removeShutdownHook(prefixCloser);
        }
        return prefixStream.digest();
    }

    public ContentHash download_(IPhysicalPrefix prefix, DigestedMessage msg, SOKID k, Version vRemote,
            @Nullable ContentHash remoteHash, @Nullable KIndex localBranchWithMatchingContent, Token tk)
            throws SQLException, IOException, ExDependsOn, ExTimeout, ExAborted, ExStreamInvalid,
            ExNoResource, ExOutOfSpace, ExNotFound, DigestException
    {
        PBGetComponentResponse response = msg.pb().getGetComponentResponse();

        // Write the new content to the prefix file
        // TODO: ideally we'd release the core lock around this entire call
        ContentHash h = writeContentToPrefixFile_(prefix, msg, response.getFileTotalLength(),
                response.getPrefixLength(), k, vRemote, localBranchWithMatchingContent, tk);

        if (remoteHash != null && !h.equals(remoteHash)) {
            l.info("{} hash mismatch: {} {} {}", msg.did(), k, remoteHash, h);
            // hash mismatch can be caused by data corruption on either end of the transfer or
            // inside the transport. Whatever the case may be, we simply can't commit the change.
            // Discard tainted prefix
            prefix.delete_();
            newMetric("gcr.hash.mismatch")
                    .addData("sokid", k.toString())
                    .addData("expected_hash", remoteHash.toHex())
                    .addData("actual_hash", h.toHex())
                    .addData("remote_did", msg.did().toStringFormal())
                    .sendAsync();
            // TODO: more specific exception
            // TODO: NAK to force the sender to recompute its local hash
            throw new ExAborted("hash mismatch");
        }

        return h;
    }

    public void apply_(SOKID k, PBGetComponentResponse response, IPhysicalPrefix prefix,
            ContentHash h, Trans t)
            throws Exception
    {
        // TODO(phoenix): validate prefix
        // lookup expected size and hash for the given version in RemoteContentDatabase

        // get length of the prefix before the actual transaction.
        long len = prefix.getLength_();

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
                    + pf.lastModified() + "," + pf.lengthOrZeroIfNotFile() + ")");
        }

        assert response.hasMtime();
        long replyMTime = response.getMtime();
        if (replyMTime < 0) throw new ExProtocolError("negative mtime");
        long mtime = _ps.apply_(prefix, pf, wasPresent, replyMTime, t);

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
        _ds.setCA_(k, len, mtime, h, t);
    }
}
