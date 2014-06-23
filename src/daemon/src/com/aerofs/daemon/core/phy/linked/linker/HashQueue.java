/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.BaseParam;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.NativeVersionControl.IVersionControlListener;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Version;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.rocklog.RockLog;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;

/**
 * When a file sees its timestamp change but not its length it is possible that the actual content
 * of the file is unchanged. In particular, this has been to plague some users at BB, resulting in
 * spurious updates, wasted network bandwidth, serious user confusion and the occasional nasty
 * interaction with Photoshop.
 *
 * To avoid that, instead of unconditionally bumping ticks in that situation we rely on content
 * hashing to avoid spurious updates. This is only possible if there is a content hash for the file
 * in the DB already. Recent changes ensure that content hashes are computed and stored whenever
 * a file is transfered to/from a peer. This ensures that, if the content hash is not present then
 * the file must have been modified locally unambiguously and not propagated yet, therefore any
 * timestamp change can safely cause a version bump.
 *
 * To avoid blocking core threads on potentially long disk I/O and hash computation, up to two
 * auxiliary threads are used. Tasks are queued for processing by these threads along with enough
 * information such that:
 *   1. identical subsequent requests are merged
 *   2. any change in the physical filesystem will abort the operation
 *
 * If the queue overflows, hashing will be done synchronously to automatically throttle incoming
 * hash requests.
 */
public class HashQueue implements IVersionControlListener
{
    private final static Logger l = Loggers.getLogger(HashQueue.class);

    private final CoreScheduler _sched;
    private final DirectoryService _ds;
    private final VersionUpdater _vu;
    private final TransManager _tm;
    private final RockLog _rl;

    private class HashRequest implements Runnable
    {
        final SOID soid;
        final InjectableFile f;
        final long length;
        final long mtime;
        final ContentHash h;

        // only ever written from a core thread
        volatile boolean aborted;

        HashRequest(SOID soid, InjectableFile f, long length, long mtime, ContentHash h)
        {
            this.soid = soid;
            this.f = f;
            this.length = length;
            this.mtime = mtime;
            this.h = h;
        }

        @Override
        public void run()
        {
            if (aborted) {
                l.debug("hash computation aborted {}", soid);
                removeSelf();
                return;
            }

            final ContentHash h = hash();
            if (h == null) {
                removeSelf();
                return;
            }

            _sched.schedule(new AbstractEBSelfHandling() {
                @Override
                public void handle_()
                {
                    commit(h);
                    removeSelf();
                }
            }, 0);
        }

        private void removeSelf()
        {
            _requests.remove(soid, this);
        }

        private ContentHash hash()
        {
            MessageDigest md = BaseSecUtil.newMessageDigest();
            try {
                InputStream is = f.newInputStream();
                try {
                    int n;
                    long total = 0;
                    byte[] bs = new byte[BaseParam.FILE_BUF_SIZE];
                    while ((n = is.read(bs)) >= 0) {
                        total += n;
                        if (total > length) {
                            l.debug("file larger than expected {}", soid);
                            return null;
                        }
                        md.update(bs, 0, n);
                        if (aborted) {
                            l.debug("hash computation aborted {}", soid);
                            return null;
                        }
                    }
                    if (total != length) {
                        l.debug("file smaller than expected {}", soid);
                        return null;
                    }
                } finally {
                    is.close();
                }
            } catch (IOException e) {
                l.debug("hash computation failed {}", soid, e);
                return null;
            }
            return new ContentHash(md.digest());
        }

        private void commit(ContentHash newHash)
        {
            if (aborted) {
                l.debug("hash computation aborted {}", soid);
                return;
            }
            try {
                Trans t = _tm.begin_();
                try {
                    updateCAHash(newHash, t);
                    t.commit_();
                } finally {
                    t.end_();
                }
            } catch (Exception e) {
                l.warn("failed to update hash", e);
            }
        }

        private void updateCAHash(ContentHash newHash, Trans t)
                throws IOException, SQLException, ExNotFound
        {
            SOKID sokid = new SOKID(soid, KIndex.MASTER);

            // make sure the CA lenght and hash correspond to the values given in the hash request
            // if they changed under our feet then we cannot safely take any action based on the
            // value of the computed hash
            OA oa = _ds.getOAThrows_(soid);
            CA ca = oa.caMasterThrows();
            ContentHash oldHash = _ds.getCAHash_(sokid);
            if (ca.length() != length || !oldHash.equals(h)) {
                l.warn("log diff, abort hash update {} ({}, {}) != ({}, {})",
                        soid, length, h, ca.length(), oldHash);
                return;
            }

            // last chance for race-condition check with the physical filesystem
            if (f.getLength() != length || f.lastModified() != mtime) {
                l.info("phy diff, abort hash update {} ({}, {}) != ({}, {})",
                        soid, length, mtime, f.getLength(), f.lastModified());
                return;
            }

            final boolean same = newHash.equals(h);

            // send rocklog defects to gather information about frequency of linker-induced
            // hashing, size distribution of affected file and benefit (or lack thereof) of
            // going through the trouble of hashing content to prevent spurious updates
            _rl.newDefect("mcn.hash." + (same ? "same" : "change"))
                    .addData("soid", soid.toString())
                    .addData("length", length)
                    .addData("db_mtime", ca.mtime())
                    .addData("fs_mtime", mtime)
                    .send();

            // update CA as originally planned, leveraging content hash to hopefully avoid
            // spurious updates (as experience by some users at BB)
            if (same) {
                l.info("mtime-only change {} {} {}", soid, ca.mtime(), mtime);
                _ds.setCA_(sokid, length, mtime, newHash, t);
            } else {
                l.info("content hash change {} {} {}", soid, h, newHash);
                _ds.setCA_(sokid, length, mtime, newHash, t);
                _vu.update_(new SOCKID(sokid, CID.CONTENT), t);
            }
        }
    }

    private final ConcurrentMap<SOID, HashRequest> _requests = Maps.newConcurrentMap();

    private final Executor _e = new ThreadPoolExecutor(
            0, 2,                                           // at most 2 threads
            1, TimeUnit.MINUTES,                            // idle thread TTL
            Queues.<Runnable>newLinkedBlockingQueue(100),          // bounded event queue
            new CallerRunsPolicy());                        // blocking submit on queue overflow

    @Inject
    public HashQueue(CoreScheduler sched, DirectoryService ds, NativeVersionControl nvc,
            VersionUpdater vu, TransManager tm, RockLog rl)
    {
        _sched = sched;
        _ds = ds;
        _vu = vu;
        _tm = tm;
        _rl = rl;
        nvc.addListener_(this);
    }

    /**
     * Asynchronously hashes the contents of the given file
     *
     * The operation is aborted if either the logical or physical object change at any point.
     *
     * Multiple requests for the same object are merged.
     *
     * NB: to avoid unbounded memory usage, a fixed length queue is used and if it overflows,
     * extra requests will be executed synchronously
     */
    public boolean requestHash_(final SOID soid, InjectableFile f, long length, long mtime,
            ContentHash h, Trans t)
    {
        HashRequest r = _requests.get(soid);
        if (r != null && !r.aborted) {
            if (r.length == length && r.mtime == mtime && r.h.equals(h)
                    && f.getAbsolutePath().equals(r.f.getAbsolutePath())) {
                // duplicate hash request with no intervening changes
                // can happen if, e.g. the file being hashed is requested by a remote
                // peer and the transfer subsystem forces a rescan when it detects an
                // inconsistency between db and fs
                return false;
            }
            // if any change occured, abort ongoing hash op and restart from scratch
            l.warn("phy diff, restart hash {}", soid);
            r.aborted = true;
        }

        final HashRequest rr = new HashRequest(soid, f, length, mtime, h);
        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                rr.aborted = true;
                rr.removeSelf();
            }
        });
        _requests.put(soid, rr);
        _e.execute(rr);
        return true;
    }

    @Override
    public void localVersionAdded_(SOCKID sockid, Version v, Trans t) throws SQLException
    {
        if (!sockid.cid().isContent()) return;
        if (!sockid.kidx().isMaster()) return;
        HashRequest r = _requests.get(sockid.soid());
        if (r != null && !r.aborted) {
            l.warn("log diff, abort hash {}", sockid.socid());
            r.aborted = true;
            r.removeSelf();
        }
    }
}
