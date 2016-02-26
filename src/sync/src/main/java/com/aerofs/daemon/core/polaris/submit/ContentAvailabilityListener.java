/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.IContentVersionListener;
import com.aerofs.daemon.core.polaris.PolarisAsyncClient;
import com.aerofs.daemon.core.polaris.api.*;
import com.aerofs.daemon.core.polaris.async.AsyncTask;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.async.AsyncWorkGroupScheduler;
import com.aerofs.daemon.core.polaris.async.AsyncWorkGroupScheduler.TaskState;
import com.aerofs.daemon.core.polaris.db.AvailableContentDatabase;
import com.aerofs.daemon.core.polaris.db.AvailableContentDatabase.AvailableContent;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.OID;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;

import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;

import javax.inject.Inject;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import static com.aerofs.daemon.core.polaris.GsonUtil.GSON;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Listens for new content versions and notifies Polaris of available content.
 * Should be scheduled after successful content downloads.
 *
 * TODO: handle loss of availability due to expulsion
 */
public class ContentAvailabilityListener implements IContentAvailabilityListener, IContentVersionListener
{
    private final static Logger l = Loggers.getLogger(ContentAvailabilityListener.class);

    private final PolarisAsyncClient _polarisClient;
    private final String _did;
    private final AvailableContentDatabase _acdb;
    private final TransManager _tm;
    private final TaskState _submitAvailableContentToPolarisTask;
    private final TransLocal<Boolean> _submitTransLocal;

    // polaris' resulting SSMP payload size is 24 bytes per location. SSMP
    // specifies 1024 bytes max in a payload, so the batch size of 42 is used to
    // stay under this limit.
    private final int MAX_BATCH_SIZE = 42;

    @Inject
    public ContentAvailabilityListener(PolarisAsyncClient client, CfgLocalDID did,
            AvailableContentDatabase acdb, TransManager tm,
            AsyncWorkGroupScheduler asyncWorkGroupScheduler, CfgLocalUser cfgLocalUser) {
        _polarisClient = client;
        _did = did.get().toStringFormal();
        _acdb = acdb;
        _tm = tm;

        _submitAvailableContentToPolarisTask = asyncWorkGroupScheduler.register_("availability",
                new AsyncTask() {
                    @Override
                    public void run_(AsyncTaskCallback cb) {
                        l.trace("ENTER: submitAvailableContentToPolarisTask.run");

                        Map<SOID, LocationBatchOperation> operations;
                        try {
                            operations = buildLocationBatchOperationsMap_();
                        } catch (SQLException e) {
                            cb.onFailure_(e);
                            return;
                        }

                        if (operations.size() == 0) {
                            cb.onSuccess_(false);
                            return;
                        }

                        _polarisClient.post("/batch/locations", new LocationBatch(operations.values()),
                                cb, response -> updateAvailableContentDatabase_(response, operations));
                        l.trace("EXIT: submitAvailableContentToPolarisTask.run");
                    }
                });

        _submitTransLocal = new TransLocal<Boolean>() {
            @Override
            protected Boolean initialValue(Trans t) {
                t.addListener_(new AbstractTransListener() {
                    @Override
                    public void committed_() {
                        _submitAvailableContentToPolarisTask.schedule_();
                    }
                });
                return true;
            }
        };
    }

    @Override
    public void start_() {
        _submitAvailableContentToPolarisTask.start_();
    }

    /**
     * attach a transaction listener to submit the available content upon
     * commit. A TransLocal is used to avoid adding redundant listeners.
     */
    @Override
    public void onSetVersion_(SIndex sidx, OID oid, long v, Trans t) throws SQLException {
        l.trace("onSetVersion_");
        _acdb.setContent_(sidx, oid, v, t);
        checkArgument(_submitTransLocal.get(t));
    }

    private Map<SOID, LocationBatchOperation> buildLocationBatchOperationsMap_() throws SQLException {
        Map<SOID, LocationBatchOperation> operations = new HashMap<>();
        try (IDBIterator<AvailableContent> contents = _acdb.listContent_()) {
            while (contents.next_() && operations.size() < MAX_BATCH_SIZE) {
                AvailableContent content = contents.get_();

                operations.put(new SOID(content.sidx, content.oid), new LocationBatchOperation(
                        content.oid.toStringFormal(), content.version, _did, LocationUpdateType.INSERT));
            }
            l.debug("buildLocationBatchOperationsList ops.size = {}", operations.size());
        }
        return operations;
    }

    private Boolean updateAvailableContentDatabase_(HttpResponse response,
            Map<SOID, LocationBatchOperation> operations)
                    throws SQLException, ExRetryLater, ExInvalidID, ExProtocolError {
        l.trace("ENTER updateAvailableContentDatabase, r.status = {}", response.getStatus());
        if (!response.getStatus().equals(HttpResponseStatus.OK)) {
            if (response.getStatus().getCode() >= 500) {
                throw new ExRetryLater(response.getStatus().getReasonPhrase());
            }
            throw new ExProtocolError(response.getStatus().getReasonPhrase());
        }

        String content = response.getContent().toString(BaseUtil.CHARSET_UTF);
        LocationBatchResult batchResult = GSON.fromJson(content, LocationBatchResult.class);

        l.debug("updateAvailableContentDatabase: ops.size = {}, results.size = {}", operations.size(),
                batchResult.results.size());

        try (Trans t = _tm.begin_()) {
            Iterator<LocationBatchOperationResult> results = batchResult.results.iterator();
            for (Entry<SOID, LocationBatchOperation> entry : operations.entrySet()) {
                LocationBatchOperation operation = entry.getValue();
                if (results.next().successful) {
                    _acdb.deleteContent_(entry.getKey().sidx(), new OID(operation.oid),
                            operation.version, t);
                    l.trace("updateAvailableContentDatabase delete {} {} successful",
                            operation.oid.toString(), operation.version);
                } else {
                    l.trace("updateAvailableContentDatabase not deleting {} {}",
                            operation.oid.toString(), operation.version);
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
