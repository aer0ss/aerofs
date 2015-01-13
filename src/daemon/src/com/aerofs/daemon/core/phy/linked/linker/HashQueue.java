/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.BaseParam;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.analytics.AnalyticsEvents.FileSavedEvent;
import com.aerofs.base.analytics.IAnalyticsEvent;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.analytics.AnalyticsEventCounter;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.aerofs.defects.Defects.newMetric;
import static com.google.common.base.Preconditions.checkState;

/**
 * Hash all the things!
 *
 * In the before time files were only hashed as a last resort to detect false content conflicts.
 *
 * Later, opportunistic hashing during transfers was added to prevent mtime-only changes from
 * causing spurious updates. This required the introduction of this HashQueue to defer CA update
 * until *after* file contents were re-hashed and an accurate decision could be made.
 *
 * Finally, with the mighty Polaris descended upon us and, to the greatest delight of an ardent
 * preacher who need not be named, it became necessary to hash (allthethings) *before* they could
 * be advertised to the rest of the world.
 *
 * Every time {@link com.aerofs.daemon.core.phy.linked.linker.MightCreateOperations} detects a
 * potential file change, it place a hash request in this queue. The request includes the length
 * and mtime of the file at the time the request is issued. This allows deduplication of identical
 * requests and detection of file changes while hashing is in progress.
 *
 * Once the file is hashed, the CA is updated as needed to reflect the new state of the file and
 * the version vector is bumped if the content hash actually changed so that the new content may
 * propagate to remote devices.
 *
 * To avoid blocking core threads on potentially long disk I/O and hash computation, up to two
 * auxiliary threads are used. If the queue overflows, hashing will be done synchronously in the
 * calling thread to throttle incoming hash requests.
 */
public class HashQueue
{
    private final static Logger l = Loggers.getLogger(HashQueue.class);

    private final CoreScheduler _sched;
    private final DirectoryService _ds;
    private final VersionUpdater _vu;
    private final TransManager _tm;
    private final TokenManager _tokenManager;
    private final AnalyticsEventCounter _saveCounter;

    enum State
    {
        PENDING,
        HASHED,
        COMMITTED
    }

    private class HashRequest implements Runnable
    {

        final SOID soid;
        final long length;
        final long mtime;

        // synchronized(this)
        InjectableFile f;

        // only ever written from a core thread holding the core lock
        volatile boolean aborted;

        // only ever written from hasher thread
        // NB: might be a core thread NOT holding the core lock
        volatile State state;

        HashRequest(SOID soid, InjectableFile f, long length, long mtime)
        {
            this.soid = soid;
            this.f = f;
            this.length = length;
            this.mtime = mtime;
            this.state = State.PENDING;
        }

        @Override
        public String toString()
        {
            return "{" + Joiner.on(',').join(soid, length, mtime, f, aborted, state) + "}";
        }

        @Override
        public void run()
        {
            if (aborted) {
                l.debug("hash computation aborted {}", soid);
                removeSelf();
                return;
            }

            final ContentHash newHash = hash();
            checkState(state == State.PENDING);
            state = State.HASHED;
            if (newHash == null) {
                // TODO: exp retry if the file exists?
                removeSelf();
                return;
            }

            _sched.schedule(new AbstractEBSelfHandling() {
                @Override
                public void handle_()
                {
                    commit_(newHash);
                    removeSelf();
                }
            }, 0);
        }

        private void removeSelf()
        {
            _requests.remove(soid, this);
        }

        private synchronized InputStream inputStream() throws IOException
        {
            return f.newInputStream();
        }

        private ContentHash hash()
        {
            MessageDigest md = BaseSecUtil.newMessageDigest();
            // TODO: pipeline I/O and CPU?
            try (InputStream is = inputStream()) {
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
            } catch (IOException e) {
                l.debug("hash computation failed {}", soid, e);
                return null;
            }
            return new ContentHash(md.digest());
        }

        private void commit_(ContentHash newHash)
        {
            if (aborted) {
                l.debug("hash computation aborted {}", soid);
                return;
            }
            try {
                // last chance for race-condition check with the physical filesystem
                if (f.wasModifiedSince(mtime, length)) {
                    l.info("phy diff, abort hash update {} ({}, {}) != ({}, {})",
                            soid, length, mtime, f.getLength(), f.lastModified());
                    return;
                }

                try (Trans t = _tm.begin_()) {
                    updateCAHash_(newHash, t);
                    t.commit_();
                }
            } catch (Exception e) {
                l.warn("failed to update hash", e);
            }
        }

        private void updateCAHash_(ContentHash newHash, Trans t)
                throws IOException, SQLException, ExNotFound
        {
            SOKID sokid = new SOKID(soid, KIndex.MASTER);

            // OA and MASTER CA MUST exist when we get here
            OA oa = _ds.getOAThrows_(soid);
            CA ca = oa.caMasterThrows();

            ContentHash oldHash = _ds.getCAHash_(sokid);
            final boolean same = oldHash != null && newHash.equals(oldHash)
                    && ca.length() == length;

            // send rocklog defects to gather information about frequency of linker-induced
            // hashing, size distribution of affected file and benefit (or lack thereof) of
            // going through the trouble of hashing content to prevent spurious updates
            newMetric("mcn.hash." + (same ? "same" : "change"))
                    .addData("soid", soid.toString())
                    .addData("length", length)
                    .sendAsync();

            // only bump version if the content hash changes
            // hopefully avoid spurious updates as experienced by some users at BB
            if (same) {
                l.info("change {} mtime {} {}", soid, ca.mtime(), mtime);
                _ds.setCA_(sokid, length, mtime, newHash, t);
            } else {
                l.info("change {} content {} {}", soid, oldHash, newHash);
                _ds.setCA_(sokid, length, mtime, newHash, t);
                state = State.COMMITTED;
                _vu.update_(new SOCKID(sokid, CID.CONTENT), t);
                _saveCounter.inc();
            }
        }
    }

    private final ConcurrentMap<SOID, HashRequest> _requests = Maps.newConcurrentMap();

    /**
     * When the bounded hash queue is full the hashing task will be executed in the calling
     * thread. To avoid starvation when large files are hashed, the core lock is first released.
     *
     * NB: this is possible because calls to {@link java.util.concurrent.Executor#execute} are
     * deferred to {@link com.aerofs.daemon.lib.db.ITransListener#committed_} instead of being
     * made immediately on reception of the hash request.
     */
    private final class CoreLockReleaseRunPolicy implements RejectedExecutionHandler
    {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor)
        {
            if (executor.isShutdown()) return;
            try {
                _tokenManager.inPseudoPause_(Cat.UNLIMITED, "blocking-hash", () -> {
                    r.run();
                    return null;
                });
            } catch (ExNoResource|ExAborted e) {
                l.warn("blocking hashing failed", e);
            }
        }
    }

    private final Executor _e = new ThreadPoolExecutor(
            0, 2,                                           // at most 2 threads
            1, TimeUnit.MINUTES,                            // idle thread TTL
            new LinkedBlockingQueue<>(10000),               // bounded event queue
            new CoreLockReleaseRunPolicy());                // blocking submit on queue overflow

    @Inject
    public HashQueue(CoreScheduler sched, DirectoryService ds, VersionUpdater vu, TransManager tm,
            TokenManager tokenManager, Analytics analytics)
    {
        _sched = sched;
        _ds = ds;
        _vu = vu;
        _tm = tm;
        _tokenManager = tokenManager;
        _saveCounter = new AnalyticsEventCounter(analytics) {
            @Override
            public IAnalyticsEvent createEvent(int count)
            {
                return new FileSavedEvent(count);
            }
        };
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
     *
     * @return true if a new request was enqueued, false if there was a matching one already
     */
    public boolean requestHash_(final SOID soid, InjectableFile f, long length, long mtime, Trans t)
    {
        HashRequest r = _requests.get(soid);
        if (r != null && !r.aborted) {
            if (r.length == length && r.mtime == mtime
                    && f.getAbsolutePath().equals(r.f.getAbsolutePath())) {
                // duplicate hash request with no intervening changes
                // can happen if, e.g. the file being hashed is requested by a remote
                // peer and the transfer subsystem forces a rescan when it detects an
                // inconsistency between db and fs
                return false;
            }
            // if any change occurred, abort ongoing hash op and restart from scratch
            l.warn("phy diff, restart hash {}", soid);
            r.aborted = true;
        }

        _tlReq.get(t).put(soid, new HashRequest(soid, f, length, mtime));
        return true;
    }

    private final TransLocal<Map<SOID, HashRequest>> _tlReq
            = new TransLocal<Map<SOID, HashRequest>>() {
        @Override
        protected Map<SOID, HashRequest> initialValue(Trans t)
        {
            final Map<SOID, HashRequest> m = Maps.newHashMap();
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committed_()
                {
                    for (Entry<SOID, HashRequest> e : m.entrySet()) {
                        _requests.put(e.getKey(), e.getValue());
                        _e.execute(e.getValue());
                    }
                }
            });
            return m;
        }
    };
}
