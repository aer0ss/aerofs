/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.PolarisContentVersionControl;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.polaris.GsonUtil;
import com.aerofs.daemon.core.polaris.PolarisAsyncClient;
import com.aerofs.daemon.core.polaris.api.Batch;
import com.aerofs.daemon.core.polaris.api.Batch.BatchOp;
import com.aerofs.daemon.core.polaris.api.BatchResult;
import com.aerofs.daemon.core.polaris.api.BatchResult.BatchOpResult;
import com.aerofs.daemon.core.polaris.api.BatchResult.PolarisError;
import com.aerofs.daemon.core.polaris.api.LocalChange;
import com.aerofs.daemon.core.polaris.api.LocalChange.Type;
import com.aerofs.daemon.core.polaris.api.UpdatedObject;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.*;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase.ContentChange;
import com.aerofs.daemon.core.protocol.ContentProvider;
import com.aerofs.daemon.core.protocol.SendableContent;
import com.aerofs.daemon.core.status.PauseSync;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;

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
public class ContentChangeSubmitter extends WaitableSubmitter<Long> implements Submitter
{
    private final static Logger l = Loggers.getLogger(ContentChangeSubmitter.class);

    private final PolarisAsyncClient _client;
    private final ChangeEpochDatabase _cedb;
    private final ContentChangesDatabase _ccdb;
    private final CentralVersionDatabase _cvdb;
    protected final RemoteLinkDatabase _rldb;
    private final RemoteContentDatabase _rcdb;
    private final PauseSync _pauseSync;
    private final TransManager _tm;
    private final ContentProvider _provider;
    private final CfgLocalDID _did;
    private final PolarisContentVersionControl _cvc;
    private final ContentSubmitConflictHandler _ch;
    private final LocalACL _lacl;
    private final CfgLocalUser _localUser;

    @Inject
    public ContentChangeSubmitter(PolarisAsyncClient client, ContentChangesDatabase ccdb,
            RemoteLinkDatabase rldb, RemoteContentDatabase rcdb, CentralVersionDatabase cvdb,
            PauseSync pauseSync, TransManager tm, ContentProvider provider, CfgLocalDID did,
            PolarisContentVersionControl cvc, ChangeEpochDatabase cedb, ContentSubmitConflictHandler ch,
            LocalACL lacl, CfgLocalUser localUser)
    {
        _client = client;
        _rldb = rldb;
        _rcdb = rcdb;
        _ccdb = ccdb;
        _cvdb = cvdb;
        _pauseSync = pauseSync;
        _tm = tm;
        _provider = provider;
        _did = did;
        _cvc = cvc;
        _cedb = cedb;
        _ch = ch;
        _lacl = lacl;
        _localUser = localUser;
    }

    @Override
    public String name()
    {
        return "content-submit";
    }

    private final int MAX_BATCH_SIZE = 100;

    /**
     * Asynchronously submit the next queued local content changes to Polaris.
     */
    @Override
    public void submit_(SIndex sidx, AsyncTaskCallback cb) throws Exception
    {
        if (_pauseSync.isPaused()) {
            l.warn("paused {}", sidx);
            cb.onSuccess_(false);
            return;
        }

        if (!_lacl.check_(_localUser.get(), sidx, Permissions.EDITOR)) {
            l.warn("not editor {}", sidx);
            cb.onFailure_(new ExNoPerm());
            return;
        }

        long highest = _cedb.getHighestChangeEpoch_(sidx);
        Long current = _cedb.getChangeEpoch_(sidx);
        // current will only be set if we've migrated to polaris, if not migrated to polaris we should ignore this check
        if (current != null && highest > current) {
            l.info("delay submit: current {} < highest {}", current, highest);
            cb.onSuccess_(false);
            return;
        }

        boolean retry = false;
        Counters cnt = new Counters();
        List<ContentChange> cc = Lists.newArrayList();
        List<BatchOp> ops = Lists.newArrayList();
        // TODO(phoenix): avoid wasteful repeated iteration of ignored entries
        // TODO(phoenix): filter out failed entries on retry to make progress?
        try (IDBIterator<ContentChange> it = _ccdb.getChanges_(sidx)) {
            while (it.next_() && ops.size() < MAX_BATCH_SIZE) {
                ContentChange c = it.get_();
                BatchOp op = op_(c, cnt);
                if (op != null) {
                    cc.add(c);
                    ops.add(op);
                } else {
                    retry = true;
                }
            }
        }

        if (cnt.meta_delay > 0) {
            l.info("delay content submit until meta submitted: {}", cnt.meta_delay);
        }
        if (cnt.hash_delay > 0) {
            l.info("delay content submit until hash computed: {}", cnt.hash_delay);
        }
        if (cnt.scan_delay > 0) {
            l.info("delay content submit for modified file: {}", cnt.scan_delay);
        }
        if (cnt.conflict > 0) {
            l.info("ignore conflict branch: {}", cnt.conflict);
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

    private static class Counters {
        long meta_delay;
        long hash_delay;
        long scan_delay;
        long conflict;
    }

    private BatchOp op_(ContentChange c, Counters cnt) throws Exception
    {
        SOID soid = new SOID(c.sidx, c.oid);

        // skip content submission until meta is successfully submitted
        if (_rldb.getParent_(c.sidx, c.oid) == null) {
            ++cnt.meta_delay;
            return null;
        }

        SendableContent content;
        try {
            content = _provider.content(new SOKID(soid, KIndex.MASTER));
        } catch (ExNotFound e) {
            l.warn("no change to submit {}", soid);
            try (Trans t = _tm.begin_()) {
                _ccdb.deleteChange_(soid.sidx(), soid.oid(), t);
                t.commit_();
            }
            return null;
        }
        if (content.hash == null) {
            ++cnt.hash_delay;
            return null;
        }

        // don't submit changes when a conflict exists
        if (_provider.hasConflict(soid)) {
            ++cnt.conflict;
            // TODO(phoenix): remove ccd entry?
            return null;
        }

        if (content.pf.wasModifiedSince(content.mtime, content.length)) {
            ++cnt.scan_delay;
            return null;
        }

        LocalChange change = new LocalChange();
        change.type = Type.UPDATE_CONTENT;
        change.localVersion = Math.max(Objects.firstNonNull(_cvdb.getVersion_(c.sidx, c.oid), 0L), 0L);
        change.mtime = content.mtime;
        change.size = content.length;
        change.hash = content.hash.toHex();

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
            BatchResult r = null;
            try {
                r = GsonUtil.GSON.fromJson(body, BatchResult.class);
            } catch (JsonSyntaxException e) {
                l.warn("invalid json", e.getMessage());
            }
            if (r == null || r.results == null) {
                throw new ExProtocolError("invalid reply: "+ r + " " + body);
            }
            if (r.results.size() > batch.operations.size()) {
                throw new ExProtocolError("invalid result size");
            }

            long ack = 0L;
            int failed = 0;
            SIndex sidx = null;

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
                    if (_ch.onConflict_(c.get(i), lc, or.errorMessage)) {
                        ++failed;
                    }
                } else {
                    // TODO(phoenix): figure out which errors need special handling
                    ++failed;
                    l.warn("batch op failed {} {} {}", or.errorCode, or.errorMessage,
                            GsonUtil.GSON.toJson(batch.operations.get(i)));
                }
            }
            if (r.results.size() != batch.operations.size() || failed > 0) {
                throw new ExRetryLater("batch not complete");
            }
            return true;
        }
        case 403:
            throw new ExNoPerm();
        default:
            l.warn("unexpected error {}\n{}", statusCode, body);
            throw new ExRetryLater("unexpected status code: " + statusCode);
        }
    }

    private void ackSubmission_(ContentChange c, LocalChange change, UpdatedObject updated, Trans t)
            throws Exception
    {
        checkState(c.oid.equals(updated.object.oid));

        l.info("content advertised {}{} {}", c.sidx, c.oid, updated.object.version);

        // FIXME: version may reflect conflict branch state instead of MASTER
        Long v = _cvdb.getVersion_(c.sidx, c.oid);
        if (v == null || v < updated.object.version) {
            _cvc.setContentVersion_(c.sidx, c.oid, updated.object.version, updated.transformTimestamp, t);
            _rcdb.deleteUpToVersion_(c.sidx, c.oid, updated.object.version, t);
            // add "remote" content entry for latest version (in case of expulsion)
            _rcdb.insert_(c.sidx, c.oid, updated.object.version, _did.get(),
                    new ContentHash(BaseUtil.hexDecode(change.hash)), change.size, t);

            long ep = Objects.firstNonNull(_cedb.getContentChangeEpoch_(c.sidx), 0L);
            _cedb.setContentChangeEpoch_(c.sidx, Math.max(ep, updated.transformTimestamp), t);

            // FIXME: remove any conflict branch
        }

        if (!_ccdb.deleteChange_(c.sidx, c.idx, t)) {
            l.info("submitted change now obsolete {}{}: {}", c.sidx, c.oid, c.idx);
        }

        notifyWaiter_(new SOID(c.sidx, c.oid), updated.object.version);
    }
}
