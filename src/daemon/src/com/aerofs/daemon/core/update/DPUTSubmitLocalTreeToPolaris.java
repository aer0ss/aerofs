package com.aerofs.daemon.core.update;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.polaris.GsonUtil;
import com.aerofs.daemon.core.polaris.PolarisAsyncClient;
import com.aerofs.daemon.core.polaris.api.*;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.*;
import com.aerofs.daemon.lib.db.IAliasDatabase;
import com.aerofs.daemon.lib.db.ISIDDatabase;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.*;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

public class DPUTSubmitLocalTreeToPolaris implements IDaemonPostUpdateTask {
    private final static int CONVERSION_BATCH_SIZE = 50;
    private final static Logger l = Loggers.getLogger(DPUTSubmitLocalTreeToPolaris.class);

    private final DirectoryService _ds;
    private final StoreDatabase _sdb;
    private final ISIDDatabase _siddb;
    private final CfgLocalUser _localUser;
    private final LocalACL _acl;
    private final TransManager _tm;
    private final NativeVersionDatabase _nvdb;
    private final CentralVersionDatabase _cvdb;
    private final MetaChangesDatabase _mcdb;
    private final RemoteContentDatabase _rcdb;
    private final ContentFetchQueueDatabase _cfdb;
    private final ContentChangesDatabase _ccdb;
    private final ChangeEpochDatabase _cedb;
    private final IAliasDatabase _adb;
    private final PolarisAsyncClient _client;
    private final Executor _executor;
    private final Map<SIndex, SID> sidx2sid = Maps.newHashMap();
    private final Map<SIndex, Long> highestTimestamps = Maps.newHashMap();

    @Inject
    public DPUTSubmitLocalTreeToPolaris(
            DirectoryService ds,
            StoreDatabase sh,
            ISIDDatabase siddb,
            CfgLocalUser localUser,
            LocalACL acl,
            TransManager tm,
            NativeVersionDatabase nvdb,
            CentralVersionDatabase cvdb,
            MetaChangesDatabase mcdb,
            RemoteContentDatabase rcdb,
            ContentFetchQueueDatabase cfdb,
            ContentChangesDatabase ccdb,
            ChangeEpochDatabase cedb,
            IAliasDatabase adb,
            PolarisAsyncClient client)
    {
        _ds = ds;
        _sdb = sh;
        _siddb = siddb;
        _localUser = localUser;
        _acl = acl;
        _tm = tm;
        _nvdb = nvdb;
        _cvdb = cvdb;
        _mcdb = mcdb;
        _rcdb = rcdb;
        _cfdb = cfdb;
        _ccdb = ccdb;
        _cedb = cedb;
        _adb = adb;
        _client = client;
        _executor = MoreExecutors.sameThreadExecutor();
    }

    @Override
    public void run() throws Exception {
        for (SIndex s : _sdb.getAll_()) {
            // if the change epoch already exists, then this store has already been traversed and we don't have to do it again
            if (_cedb.getChangeEpoch_(s) != null) continue;
            highestTimestamps.put(s, _cedb.getHighestChangeEpoch_(s));

            // skip submitting transforms to stores which we can't edit
            // can't use _acl.check here because it relies on IMapSindex2SID which will not be init'ed yet
            if (allowedToPostOperationsToStore(s)) {
                submitConversionOperationsForStore(s);
            }

            // create necessary polaris tables for the store
            // N.B. this is done after all the operations are submitted because the existence of these tables is used to short-circuit traversing the store's objects
            try (Trans t = _tm.begin_()) {
                _mcdb.createStore_(s, t);
                _rcdb.createStore_(s, t);
                _cfdb.createStore_(s, t);
                _ccdb.createStore_(s, t);
                _cedb.setChangeEpoch_(s, -1L, t);
                t.commit_();
            }
        }
    }

    private boolean allowedToPostOperationsToStore(SIndex s) throws Exception
    {
        UserID me = _localUser.get();
        if (getSID(s).equals(SID.rootSID(me))) {
            return true;
        }
        Permissions permissionsActual = _acl.get_(s).get(me);
        return permissionsActual != null && permissionsActual.covers(Permissions.EDITOR);
    }

    private void submitConversionOperationsForStore(SIndex s) throws Exception {
        List<Batch.BatchOp> changes = Lists.newArrayList();
        _ds.walk_(new SOID(s, OID.ROOT), null, new DirectoryService.IObjectWalker<SOID>() {
            @Nullable
            @Override
            public SOID prefixWalk_(SOID cookieFromParent, OA oa) throws Exception {
                SIndex sidx = oa.soid().sidx();
                OID oid = oa.soid().oid();
                if (oa.soid().oid().isRoot()) {
                    // don't need to do anything for the root object
                    return oa.soid();
                } else if (oa.isExpelled()) {
                    // don't send deleted or expelled objects to polaris
                    return null;
                }

                Version v = _nvdb.getVersion_(sidx, oid, CID.META, KIndex.MASTER);
                // sometimes version did is zero for anchors
                if (!v.isZero_() || oa.type() == OA.Type.ANCHOR) {
                    ConversionChange insert = new ConversionChange();
                    insert.type = LocalChange.Type.INSERT_CHILD;
                    insert.child = toPolarisID(oa.soid()).toStringFormal();
                    insert.childName = oa.name();
                    insert.childObjectType = fromType(oa.type());
                    insert.addVersion(v);
                    try (IDBIterator<OID> aliases = _adb.getAliases_(oa.soid())) {
                        insert.addAliases(aliases);
                    }
                    changes.add(new Batch.BatchOp(toPolarisID(new SOID(s, oa.parent())).toStringFormal(), insert));
                }

                if (oa.isFile()) {
                    v = _nvdb.getVersion_(sidx, oid, CID.CONTENT, KIndex.MASTER);
                    CA ca = oa.caMasterNullable();
                    if (!v.isZero_() && ca != null) {
                        ContentHash hash = _ds.getCAHash_(new SOKID(oa.soid(), KIndex.MASTER));
                        if (hash != null) {
                            ConversionChange update = new ConversionChange();
                            update.type = LocalChange.Type.UPDATE_CONTENT;
                            // local version is ignored on polaris
                            update.localVersion = 0L;
                            update.mtime = ca.mtime();
                            update.size = ca.length();
                            update.hash = hash.toHex();
                            update.addVersion(v);
                            changes.add(new Batch.BatchOp(oid.toStringFormal(), update));
                        }
                    }
                }

                if (changes.size() >= CONVERSION_BATCH_SIZE) {
                    new BatchSubmission(s, getSID(s), changes).blockingSubmit();
                }

                // return null on anchors so we don't walk across store boundaries
                return oa.type() == OA.Type.ANCHOR ? null : oa.soid();
            }

            @Override
            public void postfixWalk_ (SOID cookieFromParent, OA oa)throws Exception {
            }
        });
        // submit lingering changes not made into the last batch
        if (!changes.isEmpty()) {
            new BatchSubmission(s, getSID(s), changes).blockingSubmit();
        }

    }

    private SID getSID(SIndex sidx) throws SQLException
    {
        if (sidx2sid.containsKey(sidx)) {
            return sidx2sid.get(sidx);
        } else {
            SID sid = _siddb.getSID_(sidx);
            sidx2sid.put(sidx, sid);
            return sid;
        }
    }

    private ObjectType fromType(OA.Type t) {
        switch (t) {
            case FILE:
                return ObjectType.FILE;
            case DIR:
                return ObjectType.FOLDER;
            case ANCHOR:
                return ObjectType.STORE;
            default:
                throw new RuntimeException(String.format("unrecognized OA.Type %s", t));
        }
    }

    private UniqueID toPolarisID(SOID soid) throws SQLException
    {
        if (soid.oid().isRoot()) {
            return getSID(soid.sidx());
        } else {
            return soid.oid();
        }
    }

    public static class ConversionChange extends LocalChange {

        public Map<String, Long> version;
        public List<String> aliases;

        public void addVersion(Version v)
        {
            Map<String, Long> legacy = Maps.newHashMap();
            v.getAll_().entrySet().stream().forEach(e -> legacy.put(e.getKey().toStringFormal(), e.getValue().getLong()));
            this.version = legacy;
        }

        public void addAliases(IDBIterator<OID> oids) throws SQLException
        {
            List<String> aliases = Lists.newArrayList();
            while (oids.next_()) {
                aliases.add(oids.get_().toStringFormal());
            }
            this.aliases = aliases;
        }

        public Version getVersion() throws ExInvalidID
        {
            Version v = Version.empty();
            if (this.version != null) {
                for (Map.Entry<String, Long> e : this.version.entrySet()) {
                    v.set_(new DID(e.getKey()), e.getValue());
                }
            }
            return v;
        }
    }

    private class BatchSubmission implements AsyncTaskCallback
    {
        public static final int MAX_FAILURES = 10;

        private final SIndex _sidx;
        private final SID _sid;
        private final List<Batch.BatchOp> _ops;
        private int failures = 0;
        private long delay = LibParam.EXP_RETRY_MIN_DEFAULT;
        private Throwable lastError = null;
        private final Semaphore completionSignaler = new Semaphore(0);

        public BatchSubmission(SIndex sidx, SID sid, List<Batch.BatchOp> ops)
        {
            _sidx = sidx;
            _sid = sid;
            _ops = ops;
        }

        void blockingSubmit() throws Exception {
            submit();
            completionSignaler.acquire();
            if (lastError != null) {
                throw new Exception("persistently failed to submit conversion ops", lastError);
            }
        }

        void submit()
        {
            _client.post(String.format("/conversion/store/%s", _sid.toStringFormal()), new Batch(_ops), this, r -> handleResponse(_ops, r), _executor);
        }

        boolean handleResponse(List<Batch.BatchOp> ops, HttpResponse resp) throws Exception {
            int statusCode = resp.getStatus().getCode();
            String body = resp.getContent().toString(BaseUtil.CHARSET_UTF);
            switch (statusCode) {
                case 200: {
                    BatchResult r = GsonUtil.GSON.fromJson(body, BatchResult.class);
                    if (r == null || r.results == null) {
                        throw new ExProtocolError("invalid reply: " + r + " " + body);
                    }
                    if (r.results.size() > ops.size()) {
                        throw new ExProtocolError("invalid result size");
                    }

                    long maxTimestamp = r.results.stream()
                            .filter(batchOpResult -> batchOpResult.successful)
                            .map(opResult -> opResult.updated.stream()
                                    .map(update -> update.transformTimestamp).max(Long::compare).get())
                            .max(Long::compare).orElse(-1L);
                    if (maxTimestamp > highestTimestamps.get(_sidx)) {
                        try (Trans t = _tm.begin_()) {
                            _cedb.setHighestChangeEpoch_(_sidx, maxTimestamp, t);
                            highestTimestamps.put(_sidx, maxTimestamp);
                            t.commit_();
                        }
                    }

                    for (int i = 0; i < r.results.size(); ++i) {
                        BatchResult.BatchOpResult or = r.results.get(i);
                        if (or.successful) {
                            Batch.BatchOp op = ops.get(0);
                            ConversionChange change = (ConversionChange)(op.operation);
                            try (Trans t = _tm.begin_()) {
                                switch (op.operation.type) {
                                    case UPDATE_CONTENT: {
                                        _nvdb.deleteVersion_(_sidx, new OID(op.oid), CID.CONTENT, KIndex.MASTER, change.getVersion(), t);
                                        // defer local version to remote changes
                                        _cvdb.setVersion_(_sidx, new OID(or.updated.get(0).object.oid), -1L, t);
                                        break;
                                    }
                                    case INSERT_CHILD: {
                                        _nvdb.deleteVersion_(_sidx, new OID(change.child) , CID.META, KIndex.MASTER, change.getVersion(), t);
                                        break;
                                    }
                                    default: {
                                        l.error("found non conversion op in conversion op batch {}", ops.get(i));
                                        throw new Exception("unrecognized submitted op");
                                    }
                                }
                                t.commit_();
                                ops.remove(0);
                                // reset the failure counter
                                failures = 0;
                                delay = LibParam.EXP_RETRY_MIN_DEFAULT;
                            }
                        } else {
                            l.warn("conversion op failed {} {} {}", or.errorCode, or.errorMessage, GsonUtil.GSON.toJson(ops.get(i)));
                            if (or.errorCode == BatchResult.PolarisError.INSUFFICIENT_PERMISSIONS) {
                                throw new ExNoPerm();
                            }
                            throw new Exception("failed to complete operations");
                        }
                    }
                    break;
                }
                default: {
                    l.warn("non 200 status code from polaris {} {}", statusCode, body);
                    throw new Exception("failed to submit ops to polaris");
                }
            }
            assert ops.isEmpty();
            return false;
        }


        @Override
        public void onSuccess_(boolean hasMore) {
            if (hasMore) {
                failures = 0;
                delay = LibParam.EXP_RETRY_MIN_DEFAULT;
                submit();
            } else {
                // signal that the operations have been successfully submitted
                completionSignaler.release();
            }
        }

        @Override
        public void onFailure_(Throwable t) {
            if (t instanceof ExNoPerm) {
                l.warn("exiting submission early of SID {} because of insufficient perms", _sid);
                completionSignaler.release();
            }
            failures++;
            if (failures > MAX_FAILURES) {
                lastError = t;
                completionSignaler.release();
            } else {
                l.warn("error submitting conversion ops", t);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    l.error("interrupted", e);
                }
                delay = Math.min(LibParam.EXP_RETRY_MAX_DEFAULT, delay * 2);
                submit();
            }
        }
    }
}
