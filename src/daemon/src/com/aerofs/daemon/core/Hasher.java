package com.aerofs.daemon.core;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestException;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.SecUtil;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import com.aerofs.daemon.core.object.BranchDeleter;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;

import javax.annotation.Nullable;

/**
 * This class computes content hash. Because one of AeroFS's fundamental advantages is to sync large
 * files and large file sets quickly, and hash computation is expensive in these scenarios, we want
 * to avoid it as much as possible. Therefore, AeroFS computes hashes only when it detects potential
 * content conflicts, and uses hashes to eliminate false conflicts. This leads to the following
 * INVARIANT:
 *
 * Hashes on the master branch are optional, whereas non-master branches must have non-null hashes.
 *
 * The reasons to put the hasher into a separate thread, instead of using the core threads:
 *  1. Hasher is disk-heavy. We cannot do non-blocking I/O for disks. Therefore a pure event model
 *      may not perform well.
 *  2. Hasher is also CPU-bound so it's better to put to a separate processor.
 */
public class Hasher
{
    private static final Logger l = Loggers.getLogger(Hasher.class);

    private static final String FILE_MODIFIED_MSG = "Content modified while computing hash";

    private final DirectoryService _ds;
    private final NativeVersionControl _nvc;
    private final BranchDeleter _bd;
    private final TransManager _tm;
    private final IPhysicalStorage _ps;

    @Inject
    public Hasher(TransManager tm, BranchDeleter bd, DirectoryService ds,
            NativeVersionControl nvc, IPhysicalStorage ps)
    {
        _tm = tm;
        _bd = bd;
        _nvc = nvc;
        _ds = ds;
        _ps = ps;
    }

    private final Map<SOKID, Set<TCB>> _map = Maps.newHashMap();

    private void mergeBranches_(SOKID sokid, ContentHash h)
            throws SQLException, IOException, ExNotFound
    {
        assert h != null;
        assert h.equals(_ds.getCAHash_(sokid));
        SOID soid = sokid.soid();

        Map<KIndex, Version> delList = Maps.newHashMap();

        // vAddLocal may contain version already present in branch
        // to be applied (kidxApply). So it's necessary to subtract
        // version of the kidxApply branch before branches are merged
        // in the db. See "@@" for subtraction done below.
        Version vAddLocal = Version.empty();
        KIndex kidxApply = sokid.kidx();
        for (KIndex kidx: _ds.getOAThrows_(soid).cas().keySet()) {
            // Skip the branch whose hash was set in DB.
            if (kidx.equals(sokid.kidx())) continue;

            SOCKID kBranch = new SOCKID(soid, CID.CONTENT , kidx);
            ContentHash hBranch = _ds.getCAHash_(kBranch.sokid());
            assert hBranch != null;
            if (h.equals(hBranch)) {
                // If MASTER branch has matching hash then MASTER branch
                // should be chosen as the branch to apply the merge.
                if (kidx.equals(KIndex.MASTER)) {
                    Version vApply = _nvc.getLocalVersion_(
                        new SOCKID(soid, CID.CONTENT, kidxApply));
                    vAddLocal = vAddLocal.add_(vApply);
                    delList.put(kidxApply, vApply);
                    kidxApply = KIndex.MASTER;
                } else {
                    Version vBranch = _nvc.getLocalVersion_(kBranch);
                    vAddLocal = vAddLocal.add_(vBranch);
                    delList.put(kidx, vBranch);
                }

                l.debug("kidx: " + kidx + " vAddLocal: " + vAddLocal);
            }
        }

        // Look for any branches that need to be deleted/merged.
        if (!delList.isEmpty()) {
            Trans t = _tm.begin_();
            try {
                for (Entry<KIndex, Version> en : delList.entrySet()) {
                    KIndex kidx = en.getKey();
                    assert !kidx.equals(KIndex.MASTER);

                    SOCKID kDel = new SOCKID(soid, CID.CONTENT, kidx);
                    Version vDel = en.getValue();

                    l.debug("Hash: Branch to be deleted " + kidx);
                    _bd.deleteBranch_(kDel, vDel, true, t);
                }
                // See comments "@@" above.
                Version vApply = _nvc.getLocalVersion_(new SOCKID(soid, CID.CONTENT, kidxApply));
                vAddLocal = vAddLocal.sub_(vApply);

                l.debug("Final vAddLocal: " + vAddLocal + " kApply: " + kidxApply);
                _nvc.addLocalVersion_(new SOCKID(soid, CID.CONTENT, kidxApply), vAddLocal, t);
                t.commit_();
            } finally {
                t.end_();
            }
        } else {
            l.debug("Hash: No branches with same hash found");
        }
    }

    /**
     * Returns hash of the specified SOKID.
     *
     * This method should be invoked from the core thread. If hash is available in the DB and then
     * hash will be returned immediately else hash will be computed which can be IO intensive.
     *
     * Computing hash may pause thread so shouldn't be invoked in middle of a transaction.
     */
    public ContentHash computeHash_(SOKID sokid, boolean mergeBranches, Token tk)
            throws ExAborted, ExNotFound, SQLException, DigestException, IOException
    {
        ContentHash h = _ds.getCAHash_(sokid);
        if (h != null) return h;

        Set<TCB> waitingThreads = _map.get(sokid);
        if (waitingThreads == null) {
            // No other thread is computing hash, so current thread
            // will compute hash.
            waitingThreads = new HashSet<TCB>();
            Util.verify(_map.put(sokid, waitingThreads) == null);
            Exception abortException = null;
            try {
                SOCKID sockid = new SOCKID(sokid.soid(), CID.CONTENT, sokid.kidx());
                Version vBeforeHash = _nvc.getLocalVersion_(sockid);
                TCB tcb = TC.tcb();

                Util.verify(waitingThreads.add(tcb));
                try {
                    PrepareToComputeHashResult res = prepareToComputeHash_(sockid);

                    Util.verify(tk.pseudoPause_("hasher.compute") == tcb);
                    try {
                        // Compute the IO intensive hash of the file.
                        assert h == null;
                        InputStream in = res._pf.newInputStream_();
                        try {
                            h = computeHashImpl(in, res._fileLen, res._aborter);
                        } finally {
                            in.close();
                        }
                        assert h != null;
                    } finally {
                        tcb.pseudoResumed_();
                    }

                } finally {
                    Util.verify(waitingThreads.remove(tcb));
                }

                // Look for file content modification using version vectors.
                // This check is necessary for Local files.
                // TODO do the following check in checkAbortion() above?
                if (!_nvc.getLocalVersion_(sockid).sub_(vBeforeHash).isZero_())
                    throw new ExAborted(FILE_MODIFIED_MSG);

                // the object may have disappeared as a result of aliasing while we were busy
                // computing the hash, thorw ExNotFound in that case to avoid running into some
                // AssertionError later
                _ds.getOAThrows_(sokid.soid());

                Trans t = _tm.begin_();
                try {
                    _ds.setCAHash_(sokid, h, t);
                    t.commit_();
                } finally {
                    t.end_();
                }

                if (mergeBranches) mergeBranches_(sokid, h);

                // While hash was being computed other threads may have queued
                // requests to compute hash of the same file, so resume waiting
                // threads.
                for (TCB thread : waitingThreads) thread.resume_();

                return h;
            } catch (ExAborted e) {
                abortException = e;
                throw e;
            } catch (ExNotFound e) {
                abortException = e;
                throw e;
            } catch (SQLException e) {
                abortException = e;
                throw e;
            } catch (DigestException e) {
                abortException = e;
                throw e;
            } catch (IOException e) {
                abortException = e;
                throw e;
            } finally {
                if (abortException != null) {
                    for (TCB thread : waitingThreads) thread.abort_(abortException);
                }
                Util.verify(_map.remove(sokid) == waitingThreads);
            }

        } else {
            // Some thread is computing hash, so this thread will wait till computation is done.

            TCB tcb = TC.tcb();
            Util.verify(waitingThreads.add(tcb));
            try {
                tk.pause_("hasher.wait");
            } finally {
                Util.verify(waitingThreads.remove(tcb));
            }

            // Hash should now be present in DB.
            //
            // TODO: NOT TESTED: While the thread was paused it's possible that conflict branch gets
            // deleted/merged (see mergeBranches()). Exception should be thrown in such a case.
            return _ds.getCAHash_(sokid);
        }
    }

    // Following method computes hash but doesn't store it in db.
    public ContentHash computeHash_(IPhysicalPrefix pf, Token tk)
        throws ExAborted, IOException, DigestException
    {
        // Pseudo pause before computing the IO intensive hash of the file.
        TCB tcb = TC.tcb();
        Util.verify(tk.pseudoPause_("hasher.compute") == tcb);
        try {
            InputStream in = pf.newInputStream_();
            try {
                return computeHashImpl(in, pf.getLength_(), null);
            } finally {
                in.close();
            }
        } finally {
            tcb.pseudoResumed_();
        }
    }

    private static interface IAborter
    {
        // the implementer can throw at any time to abort hash computation
        void checkAbortion() throws ExAborted;
    }

    public static ContentHash computeHashImpl(InputStream is, long fileLen,
            @Nullable IAborter aborter) throws IOException, ExAborted, DigestException
    {
        MessageDigest md = SecUtil.newMessageDigest();
        int chunkSize = LibParam.FILE_BLOCK_SIZE;

        int hashSize = md.getDigestLength();
        // TODO: what happens if you have a file so large that fileLen / chunkSize * hashSize
        // overflows int?
        final int totalHashLength = (fileLen == 0) ? hashSize :
            (int)((fileLen + chunkSize - 1) / chunkSize) * hashSize;
        byte[] hash = new byte[totalHashLength];
        byte[] dataBytes = new byte[LibParam.FILE_BUF_SIZE];
        int outPos = 0;
        long totalBytesRead = 0;

        // We want to compute hash of zero length file and hence
        // loop should run at least once.
        do {
            int chunkBytes = 0;
            int numRead;
            int toRead = Math.min(chunkSize, dataBytes.length);

            // Investigate whether other IO techniques like
            // BufferedInputStream will be better?
            while ((numRead = is.read(dataBytes, 0, toRead)) != -1 &&
                   chunkBytes < chunkSize) {
                md.update(dataBytes, 0, numRead);
                chunkBytes += numRead;

                // Ensure next read doesn't go beyond LibParam.FILE_BLOCK_SIZE
                if (chunkBytes + toRead > chunkSize) {
                    toRead = chunkSize - chunkBytes;
                    assert toRead >= 0;
                    if (toRead == 0) {
                        break;
                    }
                }
            }
            assert numRead == -1 || chunkBytes == chunkSize;

            if (aborter != null) aborter.checkAbortion();

            // Store hash of the chunk
            assert outPos + hashSize <= totalHashLength : "\n\toutpos: " + outPos +
                    "\n\thashSize: " +
                    hashSize + "\n\ttotalHashLength: " + totalHashLength + "\n\ttotalBytesRead: "
                    + totalBytesRead;
            // Verified it's okay to compute hash of zero length file wherein
            // md.digest() will be invoked without any md.update() calls.
            final int outLen = md.digest(hash, outPos, hashSize);
            assert outLen == hashSize;

            outPos += hashSize;
            totalBytesRead += chunkBytes;
            l.debug("Hashed " + totalBytesRead + " bytes of " + fileLen);
        } while (totalBytesRead < fileLen);

        assert outPos == totalHashLength;
        return new ContentHash(hash);
    }

    private final class PrepareToComputeHashResult {
        final IPhysicalFile _pf;
        final long _fileLen;
        final IAborter _aborter;

        PrepareToComputeHashResult(IPhysicalFile pf, long fileLen, IAborter aborter)
        {
            _pf = pf;
            _fileLen = fileLen;
            _aborter = aborter;
        }
    }

    private PrepareToComputeHashResult prepareToComputeHash_(SOCKID k)
            throws SQLException, ExNotFound, ExAborted, IOException
    {
        final IPhysicalFile pf = _ps.newFile_(_ds.resolveThrows_(k.soid()), k.kidx());

        final long len = pf.getLength_();
        final long mtime = pf.getLastModificationOrCurrentTime_();
        IAborter aborter = new IAborter() {
            @Override
            public void checkAbortion() throws ExAborted
            {
                try {
                    if (pf.wasModifiedSince(mtime, len)) {
                        pf.onUnexpectedModification_(mtime);
                        throw new ExAborted(FILE_MODIFIED_MSG);
                    }
                } catch (IOException e) {
                    throw new ExAborted(e);
                }
            }
        };

        return new PrepareToComputeHashResult(pf, len, aborter);
    }

    /**
     * Use this method iff hash needs to be computed when it's not possible to pseudo-pause, e.g.
     * in the middle of a core database transaction.
     */
    public ContentHash computeHashBlocking_(SOKID sokid)
            throws ExNotFound, SQLException, ExAborted, IOException, DigestException
    {
        l.debug("computeHashBlocking for " + sokid);

        ContentHash h = _ds.getCAHash_(sokid);
        if (h != null) return h;

        SOCKID k = new SOCKID(sokid.soid(), CID.CONTENT, sokid.kidx());
        PrepareToComputeHashResult res = prepareToComputeHash_(k);

        InputStream in = res._pf.newInputStream_();
        try {
            h = computeHashImpl(in, res._fileLen, res._aborter);
        } finally {
            in.close();
        }
        assert h != null;
        return h;
    }
}
