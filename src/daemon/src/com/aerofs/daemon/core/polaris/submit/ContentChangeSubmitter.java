/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.polaris.PolarisClient;
import com.aerofs.daemon.core.polaris.GsonUtil;
import com.aerofs.daemon.core.polaris.api.Batch;
import com.aerofs.daemon.core.polaris.api.Batch.BatchOp;
import com.aerofs.daemon.core.polaris.api.BatchResult;
import com.aerofs.daemon.core.polaris.api.BatchResult.BatchOpResult;
import com.aerofs.daemon.core.polaris.api.BatchResult.PolarisError;
import com.aerofs.daemon.core.polaris.api.LocalChange;
import com.aerofs.daemon.core.polaris.api.LocalChange.Type;
import com.aerofs.daemon.core.polaris.api.UpdatedObject;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase.ContentChange;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.protocol.NewUpdatesSender;
import com.aerofs.daemon.core.status.PauseSync;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

/**
 * Submit content changes to Polaris
 *
 * This class handles protocol encoding/decoding and DB cleanup after successful submission.
 *
 * For now each change is submitted in its own HTTP request but in the future the /batch route
 * should be used to reduce the number of round-trips.
 */
public class ContentChangeSubmitter implements Submitter
{
    private final static Logger l = Loggers.getLogger(ContentChangeSubmitter.class);

    private final PolarisClient _client;
    private final ContentChangesDatabase _ccdb;
    private final CentralVersionDatabase _cvdb;
    private final RemoteLinkDatabase _rldb;
    private final RemoteContentDatabase _rcdb;
    private final PauseSync _pauseSync;
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final TransManager _tm;
    private final NewUpdatesSender _nus;

    @Inject
    public ContentChangeSubmitter(PolarisClient client, ContentChangesDatabase ccdb,
            RemoteLinkDatabase rldb, RemoteContentDatabase rcdb, CentralVersionDatabase cvdb,
            PauseSync pauseSync, DirectoryService ds, IPhysicalStorage ps, TransManager tm,
            NewUpdatesSender nus)
    {
        _client = client;
        _rldb = rldb;
        _rcdb = rcdb;
        _ccdb = ccdb;
        _cvdb = cvdb;
        _pauseSync = pauseSync;
        _ds = ds;
        _ps = ps;
        _tm = tm;
        _nus = nus;
    }

    @Override
    public String name()
    {
        return "content-submit";
    }

    private final int MAX_BATCH_SIZE = 100;

    /**
     * Asynchronously submit the next queued local metadata change to Polaris.
     *
     * TODO(phoenix): batch multiple changes in a single request
     */
    @Override
    public void submit_(SIndex sidx, AsyncTaskCallback cb)
            throws SQLException
    {
        if (_pauseSync.isPaused()) {
            l.warn("paused {}", sidx);
            cb.onFailure_(new ExRetryLater("paused"));
            return;
        }

        boolean retry = false;
        List<ContentChange> cc = Lists.newArrayList();
        List<BatchOp> ops = Lists.newArrayList();
        // TODO(phoenix): avoid wasteful repeated iteration of ignored entries
        // TODO(phoenix): filter out failed entries on retry to make progress?
        try (IDBIterator<ContentChange> it = _ccdb.getChanges_(sidx)) {
            while (it.next_() && ops.size() < MAX_BATCH_SIZE) {
                ContentChange c = it.get_();
                BatchOp op = op_(c);
                if (op != null) {
                    cc.add(c);
                    ops.add(op);
                } else {
                    retry = true;
                }
            }
        }


        if (ops.isEmpty()) {
            // TODO(phoenix): make sure ignored entries are submitted promptly on condition change
            if (retry) {
                cb.onFailure_(new ExRetryLater("only ignored entries left"));
            } else {
                cb.onSuccess_(false);
            }
        } else {
            batchSubmit(cc, new Batch(ops), cb);
        }
    }

    private BatchOp op_(ContentChange c)
            throws SQLException
    {
        OA oa = _ds.getOA_(new SOID(c.sidx, c.oid));

        // skip content submission until meta is successfully submitted
        if (_rldb.getParent_(c.sidx, c.oid) == null) {
            l.info("delay content submit until meta submitted");
            return null;
        }

        // don't submit changes when a conflict exists
        if (oa.cas().size() != 1) {
            l.info("ignore conflict branch");
            // TODO(phoenix): remove ccbd entry?
            return null;
        }
        CA ca = oa.caMaster();

        ContentHash h = _ds.getCAHash_(new SOKID(oa.soid(), KIndex.MASTER));
        if (h == null) {
            l.info("delay content submit until hash computed");
            return null;
        }

        try {
            IPhysicalFile pf = _ps.newFile_(_ds.resolve_(oa), KIndex.MASTER);
            if (pf.wasModifiedSince(ca.mtime(), ca.length())) {
                l.info("delay content submit for modified file");
                return null;
            }
        } catch (IOException e) {
            return null;
        }

        LocalChange change = new LocalChange();
        change.type = Type.UPDATE_CONTENT;
        change.localVersion = Objects.firstNonNull(_cvdb.getVersion_(c.sidx, c.oid), 0L);
        change.mtime = ca.mtime();
        change.size = ca.length();
        change.hash = h.toHex();

        return new BatchOp(c.oid.toStringFormal(), change);
    }

    private void batchSubmit(List<ContentChange> c, Batch batch, AsyncTaskCallback cb)
    {
        _client.post("/batch/transforms", batch, cb, r -> handleBatch_(c, batch, r));
    }

    private boolean handleBatch_(List<ContentChange> c, Batch batch, HttpResponse resp)
            throws Exception
    {
        int statusCode = resp.getStatus().getCode();
        String body = resp.getContent().toString(BaseUtil.CHARSET_UTF);
        switch (statusCode) {
        case 200: {
            BatchResult r = GsonUtil.GSON.fromJson(body, BatchResult.class);
            if (r.results.size() > batch.operations.size()) throw new ExProtocolError();

            long ack = 0L;
            SIndex sidx = null;

            // TODO: optimistic transaction merging
            try {
                for (int i = 0; i < r.results.size(); ++i) {
                    BatchOpResult or = r.results.get(i);
                    LocalChange lc = batch.operations.get(i).operation;
                    if (or.successful) {
                        if (or.updated.size() != 1) throw new ExProtocolError();
                        try (Trans t = _tm.begin_()) {
                            ackSubmission_(c.get(i), lc, or.updated.get(0), t);
                            t.commit_();
                        }
                        if (or.updated.size() > 0) {
                            if (sidx == null) {
                                sidx = c.get(i).sidx;
                            } else {
                                // we segregate update submission by store
                                checkState(sidx == c.get(i).sidx);
                            }
                            ack = Math.max(ack, or.updated.get(0).transformTimestamp);
                        }
                    } else if (or.errorCode == PolarisError.VERSION_CONFLICT) {
                        onConflict_(c.get(i), lc, "");
                    } else {
                        // TODO(phoenix): ???
                        l.warn("batch op failed {} {} {}", or.errorCode, or.errorMessage, GsonUtil.GSON.toJson(batch.operations.get(i)));
                    }
                }
            } finally {
                if (sidx != null && ack > 0) {
                    _nus.sendForStore_(sidx, ack);
                }
            }
            return true;
        }
        default:
            l.warn("unexpected error {}\n{}", statusCode, body);
            throw new ExRetryLater("unexpected status code: " + statusCode);
        }
    }

    private boolean onConflict_(ContentChange c, LocalChange change, String body)
    {
        l.info("conflict {} {}: {}", c.sidx, c.oid, body);
        // TODO: ?
        return true;
    }

    private void ackSubmission_(ContentChange c, LocalChange change, UpdatedObject updated, Trans t)
            throws Exception
    {
        checkState(c.oid.equals(updated.object.oid));

        l.info("content advertised {}{} {}", c.sidx, c.oid, updated.object.version);
        Long v = _cvdb.getVersion_(c.sidx, c.oid);
        if (v == null || v < updated.object.version) {
            _cvdb.setVersion_(c.sidx, c.oid, updated.object.version, t);
            _rcdb.deleteUpToVersion_(c.sidx, c.oid, updated.object.version, t);
            // add "remote" content entry for latest version (in case of expulsion)
            _rcdb.insert_(c.sidx, c.oid, updated.object.version, Cfg.did(),
                    new ContentHash(BaseUtil.hexDecode(change.hash)), change.size, t);
        }

        if (!_ccdb.deleteChange_(c.sidx, c.idx, t)) {
            l.info("submitted change now obsolete {}{}: {}", c.sidx, c.oid, c.idx);
        }

        // TODO: ?
    }
}
