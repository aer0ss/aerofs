/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.net.ResponseStream;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PrefixOutputStream;
import com.aerofs.daemon.core.protocol.OngoingTransfer.End;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.*;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static com.aerofs.defects.Defects.newMetric;
import static com.google.common.base.Preconditions.checkState;

public class ContentReceiver
{
    private static final Logger l = Loggers.getLogger(ContentReceiver.class);

    private final PrefixVersionControl _pvc;
    private final IPhysicalStorage _ps;
    private final DownloadState _dlState;
    private final IncomingStreams _iss;
    protected final TransManager _tm;
    private final CoreScheduler _sched;
    private final ProgressIndicators _pi = ProgressIndicators.get();

    protected final Set<OngoingTransfer> _ongoing = new HashSet<>();

    @Inject
    public ContentReceiver(PrefixVersionControl pvc, IPhysicalStorage ps, DownloadState dlState,
                           IncomingStreams iss, TransManager tm, CoreScheduler sched)
    {
        _pvc = pvc;
        _ps = ps;
        _dlState = dlState;
        _iss = iss;
        _tm = tm;
        _sched = sched;
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

            if (!vPrefixOld.equals(vRemote)) {
                throw new ExAborted("mismatching prefix version " + k + ": "
                        + vPrefixOld + " != " + vRemote);
            }

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

    protected void onMissingConflictBranch_(SOKID k)
            throws SQLException, IOException, ExNotFound
    {
        checkState(false, "expected conflict branch is missing: %s", k);
    }

    private @Nonnull ContentHash writeContentToPrefixFile_(IPhysicalPrefix prefix, ResponseStream rs,
            final long totalFileLength, final long prefixLength, SOKID k,
            Version vRemote, @Nullable IPhysicalFile matchingContent, Token tk)
            throws ExNotFound, ExAborted, ExNoResource, SQLException, IOException, DigestException
    {
        final boolean isStreaming = rs.streamKey() != null;

        // Open the prefix file for writing, updating it as required
        final PrefixOutputStream prefixStream = createPrefixOutputStreamAndUpdatePrefixState_(
                prefix, k, vRemote, prefixLength, matchingContent != null, isStreaming);

        // PrefixOutputStream relies on close() being called to preserve the SHA256 internal
        // state. If the internal state is not saved properly, further attempt to reopen the
        // prefix will fail as it will be considered corrupted.
        // Unfortunately the UI will sometimes stop the daemon, for instance when installing
        // an update or relocating the root anchor. To avoid losing wasting progress made on
        // ongoing downloads we use a shutdown hook to maximize the likelihood of the prefix
        // stream being cleanly closed in such circumstances.
        Thread prefixCloser = new Thread(() -> {
            try { prefixStream.close(); } catch (IOException e) {}
        }, "closer");
        try {
            Runtime.getRuntime().addShutdownHook(prefixCloser);
        } catch (Exception e) {
            // trying to add a shutdown hook during shutdown causes an exception
            // however there is no way to tell that shutdown has been initiated...
            l.info("failed to add shutdown hook", BaseLogUtil.suppress(e));
        }

        try {
            // TODO: block storage could efficiently leverage a copy() primitive
            if (matchingContent != null && isStreaming) {
                // We have this file locally and we are receiving the remote content via a stream.
                // We can cancel the stream here and use the local content.
                l.debug("reading content from local branch");

                // Stop the stream since we will not be reading it from now on
                _iss.end_(rs.streamKey());

                try {
                    // release core lock to avoid blocking while copying a large prefix
                    TCB tcb = tk.pseudoPause_("cp-prefix");
                    try (InputStream is = matchingContent.newInputStream()) {
                        _pi.startSyscall();
                        ByteStreams.copy(is, prefixStream);
                    } finally {
                        _pi.endSyscall();
                        tcb.pseudoResumed_();
                    }
                } catch (FileNotFoundException e) {
                    if (!matchingContent.sokid().kidx().isMaster()) {
                        onMissingConflictBranch_(matchingContent.sokid());
                    }
                    l.warn("can't copy content from local branch {}", matchingContent);

                    throw new ExAborted(e.getMessage());
                }
            } else {
                // We do not have the content locally, or we do but we are receiving the update via
                // a datagram. The datagram is fully received at this point so there is no need to
                // read from the file system the same content we already have in memory.
                l.info("{} {} dl {} bytes", rs.did(), k, totalFileLength - prefixLength);

                // assert after opening the stream otherwise the file length may
                // have changed after the assertion and before newOutputStream()
                checkState(prefix.getLength_() == prefixLength,
                        "%s %s != %s", k, prefix.getLength_(), prefixLength);

                OngoingTransfer dl = new OngoingTransfer(_sched, _dlState, rs.ep(), k.soid(), totalFileLength);
                _ongoing.add(dl);
                try {
                    try {
                        TCB tcb = isStreaming ? tk.pseudoPause_("write") : null;
                        try {
                            writePrefix(rs.is(), prefixStream, totalFileLength - prefixLength, dl);
                        } finally {
                            if (tcb != null) tcb.pseudoResumed_();
                        }
                    } catch (ExAborted e) {
                        prefixStream.close();
                        prefix.delete_();
                        throw e;
                    }
                    // IMPORTANT: mark transfer as done to avoid race between any scheduled progress
                    // notification and the end notification
                    dl.done_(End.SUCCESS);
                } catch (Exception e) {
                    dl.done_(End.FAILURE);
                    throw e;
                } finally {
                    _ongoing.remove(dl);
                }
            }
        } finally {
            prefixStream.close();
            try {
                Runtime.getRuntime().removeShutdownHook(prefixCloser);
            } catch (IllegalStateException e) {
                // sigh, this is so damn stupid...
                // removing a hook after shutdown is started throws an exception but of course
                // there is no way to tell if a shutdown has been initiated...
                l.info("failed to remove shutdown hook", BaseLogUtil.suppress(e));
            }
        }
        return prefixStream.digest();
    }

    private void writePrefix(InputStream is, PrefixOutputStream prefixStream, long remaining,
                             OngoingTransfer ongoing)
            throws IOException, ExAborted {
        ElapsedTimer timer = new ElapsedTimer();

        byte[] buf = new byte[4096];
        while (remaining > 0) {
            // sending notifications is not cheap, hence the rate-limiting
            if (timer.elapsed() > DaemonParam.NOTIFY_THRESHOLD) {
                if (ongoing.aborted()) {
                    throw new ExAborted();
                }
                ongoing.progress(remaining);
                timer.restart();
            }
            int n = is.read(buf, 0, (int) Math.min(buf.length, remaining));
            if (n == -1) throw new EOFException();
            remaining -= n;
            _pi.incrementMonotonicProgress();
            prefixStream.write(buf, 0, n);
            l.trace("written {}>{}", n, remaining);
        }
    }

    public ContentHash download_(IPhysicalPrefix prefix, ResponseStream rs, SOKID k, Version vRemote,
            long length, long prefixLength, @Nullable ContentHash remoteHash,
            @Nullable IPhysicalFile matchingContent, Token tk)
            throws SQLException, IOException, ExAborted, ExNoResource, ExNotFound, DigestException
    {
        // Write the new content to the prefix file
        ContentHash h = writeContentToPrefixFile_(prefix, rs, length, prefixLength, k, vRemote,
                matchingContent, tk);

        if (remoteHash != null && !h.equals(remoteHash)) {
            l.info("{} hash mismatch: {} {} {}", rs.did(), k, remoteHash, h);
            // hash mismatch can be caused by data corruption on either end of the transfer or
            // inside the transport. Whatever the case may be, we simply can't commit the change.
            // Discard tainted prefix
            prefix.delete_();
            newMetric("gcr.hash.mismatch")
                    .addData("sokid", k.toString())
                    .addData("expected_hash", remoteHash.toHex())
                    .addData("actual_hash", h.toHex())
                    .addData("remote_did", rs.did().toStringFormal())
                    .sendAsync();
            // TODO: more specific exception
            // TODO: NAK to force the sender to recompute its local hash
            throw new ExAborted("hash mismatch");
        }

        return h;
    }
}
