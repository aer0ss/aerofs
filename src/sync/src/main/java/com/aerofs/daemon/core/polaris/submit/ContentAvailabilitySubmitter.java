/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.IContentVersionListener;
import com.aerofs.daemon.core.polaris.WaldoAsyncClient;
import com.aerofs.daemon.core.polaris.api.LocationBatch;
import com.aerofs.daemon.core.polaris.api.LocationBatchOperation;
import com.aerofs.daemon.core.polaris.api.LocationBatchResult;
import com.aerofs.daemon.core.polaris.async.AsyncWorkGroupScheduler;
import com.aerofs.daemon.core.polaris.async.AsyncWorkGroupScheduler.TaskState;
import com.aerofs.daemon.core.polaris.db.AvailableContentDatabase;
import com.aerofs.daemon.core.polaris.db.AvailableContentDatabase.AvailableContent;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;

import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;

import javax.inject.Inject;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.aerofs.daemon.core.polaris.GsonUtil.GSON;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Listens for new content versions and notifies Polaris of available content.
 * Should be scheduled after successful content downloads.
 *
 * TODO: handle loss of availability due to expulsion
 */
public class ContentAvailabilitySubmitter extends WaitableSubmitter<Void>
        implements IContentAvailabilityListener, IContentVersionListener
{
    private final static Logger l = Loggers.getLogger(ContentAvailabilitySubmitter.class);

    private final WaldoAsyncClient _waldoClient;
    private final AvailableContentDatabase _acdb;
    private final TransManager _tm;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;
    private final TaskState _submitTask;
    private final TransLocal<Boolean> _tlSubmit;


    // polaris' resulting SSMP payload size is 24 bytes per location. SSMP
    // specifies 1024 bytes max in a payload, so the batch size of 42 is used to
    // stay under this limit.
    private final int MAX_BATCH_SIZE = 42;

    @Inject
    public ContentAvailabilitySubmitter(WaldoAsyncClient.Factory factWaldo,
                                        AvailableContentDatabase acdb, TransManager tm,
                                        AsyncWorkGroupScheduler sched, IMapSIndex2SID sidx2sid,
                                        IMapSID2SIndex sid2sidx) {
        _waldoClient = factWaldo.create();
        _acdb = acdb;
        _sid2sidx = sid2sidx;
        _sidx2sid = sidx2sid;
        _tm = tm;

        _submitTask = sched.register_("availability", cb -> {
            LocationBatch batch;
            try {
                batch = buildLocationBatch_();
            } catch (SQLException e) {
                l.warn("failed building operations map", e);
                cb.onFailure_(e);
                return;
            }

            if (batch == null || batch.available.isEmpty()) {
                cb.onSuccess_(false);
                return;
            }

            _waldoClient.post("/submit", batch, cb,
                    r -> updateAvailableContentDatabase_(r, batch));
        });

        _tlSubmit = new TransLocal<Boolean>() {
            @Override
            protected Boolean initialValue(Trans t) {
                t.addListener_(new AbstractTransListener() {
                    @Override
                    public void committed_() {
                        _submitTask.schedule_();
                    }
                });
                return true;
            }
        };
    }

    @Override
    public void start_() {
        _submitTask.start_();
    }

    /**
     * attach a transaction listener to submit the available content upon
     * commit. A TransLocal is used to avoid adding redundant listeners.
     */
    @Override
    public void onSetVersion_(SIndex sidx, OID oid, long v, Trans t) throws SQLException {
        l.trace("onSetVersion_");
        _acdb.setContent_(sidx, oid, v, t);
        checkArgument(_tlSubmit.get(t));
    }

    @Override
    public void onContentUnavailable_(SIndex sidx, OID oid, Trans t) throws SQLException {
        // NB: version 0 is interpreted as unavailability by waldo
        // this is arguably not great from a purely semantic perspective
        // but it has the nice benefit of allowing availability and unavailability info
        // to be batched together without requiring schema and protocol changes
        onSetVersion_(sidx, oid, 0, t);
    }

    private LocationBatch buildLocationBatch_() throws SQLException {
        try (IDBIterator<AvailableContent> contents = _acdb.listContent_()) {
            if (!contents.next_()) return null;

            AvailableContent content = contents.get_();
            SIndex sidx = content.sidx;
            SID sid = _sidx2sid.get_(sidx);

            List<LocationBatchOperation> ops = new ArrayList<>();
            ops.add(new LocationBatchOperation(content.oid.toStringFormal(), content.version));

            while (contents.next_() && ops.size() < MAX_BATCH_SIZE) {
                content = contents.get_();
                if (!content.sidx.equals(sidx)) break;
                ops.add(new LocationBatchOperation(content.oid.toStringFormal(), content.version));
            }
            l.debug("buildLocationBatchOperationsList ops.size = {}", ops.size());
            return new LocationBatch(sid.toStringFormal(), ops);
        }
    }

    private Boolean updateAvailableContentDatabase_(HttpResponse response, LocationBatch batch)
                    throws Exception {
        l.trace("ENTER updateAvailableContentDatabase, r.status = {}", response.getStatus());
        if (!response.getStatus().equals(HttpResponseStatus.OK)) {
            if (response.getStatus().getCode() >= 500) {
                throw new ExRetryLater(response.getStatus().getReasonPhrase());
            }
            throw new ExProtocolError(response.getStatus().getReasonPhrase());
        }

        String content = response.getContent().toString(BaseUtil.CHARSET_UTF);
        LocationBatchResult batchResult = GSON.fromJson(content, LocationBatchResult.class);

        l.debug("updateAvailableContentDatabase: ops.size = {}, results.size = {}", batch.available.size(),
                batchResult.results.size());

        SIndex sidx = _sid2sidx.getNullable_(new SID(batch.sid));
        if (sidx == null) throw new ExNotFound("store expelled: " + batch.sid);

        if (batchResult.results.size() != batch.available.size()) {
            throw new ExProtocolError("mismatching response size");
        }

        try (Trans t = _tm.begin_()) {
            for (int i = 0; i < batchResult.results.size(); ++i) {
                LocationBatchOperation op = batch.available.get(i);
                if (batchResult.results.get(i)) {
                    OID oid = new OID(op.oid);
                    _acdb.deleteContent_(sidx, oid, op.version, t);
                    notifyWaiter_(new SOID(sidx, oid), null);

                    l.debug("updateAvailableContentDatabase delete {}{} {} successful", sidx,
                            op.oid, op.version);
                } else {
                    l.debug("updateAvailableContentDatabase not deleting {}{} {}", sidx,
                            op.oid, op.version);
                }
            }
            t.commit_();
        }

        l.trace("EXIT updateAvailableContentDatabase");

        // returns true so that it will be rescheduled to pick up any additional
        // changes
        return true;
    }
}
