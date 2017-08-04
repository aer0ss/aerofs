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
import com.aerofs.daemon.core.polaris.api.Batch;
import com.aerofs.daemon.core.polaris.api.Batch.BatchOp;
import com.aerofs.daemon.core.polaris.api.BatchResult;
import com.aerofs.daemon.core.polaris.api.BatchResult.PolarisError;
import com.aerofs.daemon.core.polaris.api.LocalChange;
import com.aerofs.daemon.core.polaris.api.LocalChange.Type;
import com.aerofs.daemon.core.polaris.api.ObjectType;
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
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;

import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

public class DPUTSubmitLocalTreeToPolaris extends PhoenixDPUT {
    private final static int CONVERSION_BATCH_SIZE = 50;
    private final static Logger l = Loggers.getLogger(DPUTSubmitLocalTreeToPolaris.class);

    private final IDBCW _dbcw;
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
            IDBCW dbcw,
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
        _dbcw = dbcw;
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
    public void runPhoenix() throws Exception {
        DPUTSyncStatusTableAlterations.addSyncStatusColumnsToOA(_dbcw);
        int failed = 0;
        for (SIndex s : _sdb.getAll_()) {
            // if the change epoch already exists, then this store has already been traversed and we don't have to do it again
            if (_cedb.getChangeEpoch_(s) != null) continue;
            highestTimestamps.put(s, _cedb.getHighestChangeEpoch_(s));

            // create these tables before conversion as they may be filled in case of name conflict
            if (!_dbcw.tableExists(MetaChangesDatabase.tableName(s))) {
                try (Trans t = _tm.begin_()) {
                    _mcdb.createStore_(s, t);
                    _ccdb.createStore_(s, t);
                    t.commit_();
                }
            }

            // skip submitting transforms to stores which we can't edit
            // can't use _acl.check here because it relies on IMapSindex2SID which will not be init'ed yet
            if (allowedToPostOperationsToStore(s)) {
                try {
                    convertStoreCatchNoPerm(s);
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    ++failed;
                    l.warn("incomplete conversion {}", s, e);
                    // attempt to make progress on other stores
                    continue;
                }
            }

            // create necessary polaris tables for the store
            // N.B. this is done after all the operations are submitted because the existence of
            // these tables is used to short-circuit traversing the store's objects
            try (Trans t = _tm.begin_()) {
                _rcdb.createStore_(s, t);
                _cfdb.createStore_(s, t);
                _cedb.setChangeEpoch_(s, -1L, t);
                t.commit_();
            }
        }
        if (failed != 0) throw new Exception("" + failed + " shared folders could not be converted");
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

    private void convertStoreCatchNoPerm(SIndex s) throws Exception {
        try {
            submitConversionOperationsForStore(s);
        } catch (Exception e) {
            if (e.getCause() instanceof ExNoPerm) {
                l.warn("exiting submission early of sidx {} because of insufficient perms", s);
            } else {
                throw e;
            }
        }
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

                // if parent is in meta change table
                // this means parent (or one of its ancestors) ran into a name conflict
                // therefore any attempt to submit this object will fail
                // simply add the object to the meta (and content) change table(s)
                // regular fetch/submit machinery will take care of conflict resolution later
                boolean defer = _mcdb.getFirstChange_(sidx, oa.parent()) != null;

                Version v = _nvdb.getVersion_(sidx, oid, CID.META, KIndex.MASTER);
                // sometimes version did is zero for anchors
                if (!v.isZero_() || oa.type() == OA.Type.ANCHOR) {
                    if (defer) {
                        try (Trans t = _tm.begin_()) {
                            _nvdb.deleteVersion_(sidx, oid, CID.META, KIndex.MASTER, v, t);
                            _mcdb.insertChange_(sidx, oid, oa.parent(), oa.name(), t);
                            t.commit_();
                        }
                    } else {
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
                }

                if (oa.isFile()) {
                    v = _nvdb.getVersion_(sidx, oid, CID.CONTENT, KIndex.MASTER);
                    CA ca = oa.caMasterNullable();
                    ContentHash hash = v.isZero_() || ca == null ? null
                            : _ds.getCAHash_(new SOKID(oa.soid(), KIndex.MASTER));
                    if (hash != null) {
                        if (defer) {
                            try (Trans t = _tm.begin_()) {
                                _nvdb.deleteVersion_(sidx, oid, CID.CONTENT, KIndex.MASTER, v, t);
                                _ccdb.insertChange_(sidx, oid, t);
                                t.commit_();
                            }
                        } else {
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
                throw new Exception("persistent failure to submit conversion ops", lastError);
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
                                        _nvdb.deleteVersion_(_sidx, new OID(change.child), CID.META, KIndex.MASTER, change.getVersion(), t);
                                        break;
                                    }
                                    default: {
                                        l.error("found non conversion op in conversion op batch {}", ops.get(i));
                                        throw new Exception("unrecognized submitted op");
                                    }
                                }
                                t.commit_();
                            }
                            ops.remove(0);
                            // reset the failure counter
                            failures = 0;
                            delay = LibParam.EXP_RETRY_MIN_DEFAULT;
                        } else if (or.errorCode == PolarisError.NAME_CONFLICT
                                || or.errorCode == PolarisError.NO_SUCH_OBJECT) {
                            l.warn("{} {}", or.errorCode, GsonUtil.GSON.toJson(ops.get(i)));
                            Batch.BatchOp op = ops.get(0);
                            ConversionChange change = (ConversionChange) (op.operation);
                            if (change.type != Type.INSERT_CHILD) throw new ExProtocolError();
                            int n = 1;
                            try (Trans t = _tm.begin_()) {
                                OID oid = new OID(change.child);
                                _nvdb.deleteVersion_(_sidx, oid, CID.META, KIndex.MASTER, change.getVersion(), t);
                                // NB: ensure later conflict resolution
                                _mcdb.insertChange_(_sidx, oid, new OID(op.oid), change.childName, t);
                                // NB: if the object is a folder we need to omit all children as well
                                // instead of going through the list of ops and removing those that
                                // match, simply handle NO_SUCH_OBJECT like NAME_CONFLICT
                                // This results in redundant (and easily avoidable) requests but it
                                // keeps the code simple and honestly at this point optimizing the
                                // conversion process is not a requirement
                                if (change.childObjectType == ObjectType.FILE && ops.size() > 1) {
                                    BatchOp next = ops.get(1);
                                    // NB: walk that generates conversion ops ensures UPDATE_CONTENT
                                    // must immediately follow matching INSERT_CHILD
                                    if (next.operation.type == Type.UPDATE_CONTENT
                                            && oid.equals(new OID(next.oid))) {
                                        n = 2;
                                        _nvdb.deleteVersion_(_sidx, oid, CID.CONTENT, KIndex.MASTER,
                                                ((ConversionChange) next.operation).getVersion(), t);
                                        // NB: ensure later conflict resolution
                                        _ccdb.insertChange_(_sidx, oid, t);
                                    }
                                }
                                t.commit_();
                            }
                            // remove conflicting op(s)
                            for (int k = 0; k < n; ++k) ops.remove(0);
                            // retry without conflicting ops
                            return !ops.isEmpty();
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
                lastError = t;
                completionSignaler.release();
                return;
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
