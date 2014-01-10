/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.activity;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.audit.client.AuditClient.AuditableEvent;
import com.aerofs.audit.client.AuditorFactory;
import com.aerofs.base.BaseParam.Audit;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.AuditDatabase;
import com.aerofs.daemon.lib.db.IActivityLogDatabase.ActivityRow;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.sv.client.SVClient;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.annotations.SerializedName;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Service that reads events from the local
 * {@link com.aerofs.daemon.core.activity.ActivityLog} and
 * reports them to the centralized audit service.
 * <p/>
 * This implementation posts events to the auditor
 * every {@link com.aerofs.base.BaseParam.Audit#AUDIT_POSTING_INTERVAL}
 * milliseconds.
 */
public final class ClientAuditEventReporter
{
    private static final int AUDIT_EVENT_BATCH_SIZE = 10;
    private static final int MAX_ACTIVITY_LOG_EVENTS_TO_ITERATE_OVER_ON_EACH_RUN = 200;

    private static final String LOCAL_EVENT_NAME = "file.notification";
    private static final String REMOTELY_REQUESTED_EVENT_NAME = "file.transfer";

    private static final Logger l = LoggerFactory.getLogger(ClientAuditEventReporter.class);

    private final DID _localdid;
    private final String _hexEncodedLocalDid;
    private final TokenManager _tokenManager;
    private final Scheduler _scheduler;
    private final TransManager _tm;
    private final AuditDatabase _auditDatabase;
    private final ActivityLog _activityLog;
    private final IMapSIndex2SID _sidxTosid;
    private final SimpleDateFormat _dateFormat;
    private final AuditClient _auditClient = new AuditClient(); // FIXME (AG): are we setting up a global audit client somewhere? Should this be injected?

    private boolean running = false;
    private long lastActivityLogIndex;

    //---------------------------------
    // GSON objects
    //

    @SuppressWarnings("unused") // fields are used within gson
    private static class PathComponents
    {
        @SerializedName("sid")
        private final String sidString;

        @SerializedName("relative_path")
        private final String relativePath;

        private PathComponents(String sidStringFormal, String relativePath)
        {
            this.sidString = sidStringFormal;
            this.relativePath = relativePath;
        }
    }

    @SuppressWarnings("unused") // fields are used within gson
    private static class SOID
    {
        @SerializedName("sid")
        private final String sidString;

        @SerializedName("oid")
        private final String oidString;

        private SOID(String sidString, String oidString)
        {
            this.sidString = sidString;
            this.oidString = oidString;
        }
    }

    //---------------------------------
    // caer implementation
    //

    private class EventBatch
    {
        private final long lastActivityLogIndex;
        private final List<AuditableEvent> reportableEvents;

        private EventBatch(long lastActivityLogIndex, List<AuditableEvent> reportableEvents)
        {
            this.lastActivityLogIndex = lastActivityLogIndex;
            this.reportableEvents = reportableEvents;
        }

        private boolean hasEvents() {
            return !reportableEvents.isEmpty();
        }

        private int numEvents() {
            return reportableEvents.size();
        }
    }

    @Inject
    public ClientAuditEventReporter(
            CfgLocalDID localdid,
            TokenManager tokenManager,
            CoreScheduler scheduler,
            TransManager tm,
            AuditDatabase auditDatabase,
            ActivityLog activityLog,
            IMapSIndex2SID sidxTosid)
    {
        _localdid = localdid.get();
        _hexEncodedLocalDid = localdid.get().toStringFormal();
        _tokenManager = tokenManager;
        _scheduler = scheduler;
        _tm = tm;
        _auditDatabase = auditDatabase;
        _activityLog = activityLog;
        _sidxTosid = sidxTosid;
        _dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        _dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        _auditClient.setAuditorClient(AuditorFactory.create());
    }

    /**
     * Loads the activity log index <strong>after</strong> from the
     * audit database after which to start posting events to the auditor.
     *
     * @throws SQLException if the data cannot be loaded from the audit database
     */
    public void init_()
            throws SQLException
    {
        setActivityLogIndexFromWhichToStartReporting_();
    }

    /**
     * Start the service.
     * <p/>
     * Schedules a audit report
     * {@link com.aerofs.base.BaseParam.Audit#START_POSTING_AUDIT_EVENTS_AFTER}
     * milliseconds after this method is called. Subsequent reports are
     * scheduled every {@link com.aerofs.base.BaseParam.Audit#AUDIT_POSTING_INTERVAL}
     * milliseconds.
     * <p/>
     * Once {@code start_} is called, subsequent calls are noops.
     */
    public synchronized void start_()
    {
        l.info("starting caer");

        if (running) return;

        running = true;

        scheduleReport_(Audit.START_POSTING_AUDIT_EVENTS_AFTER);
    }

    // set the initial index from which
    // we should start POSTing activity log entries
    private void setActivityLogIndexFromWhichToStartReporting_()
            throws SQLException
    {
        lastActivityLogIndex = _auditDatabase.getLastReportedActivityRow_();
    }

    private void scheduleReport_(long runAfter)
    {
        l.debug("schedule audit report after {}ms", runAfter);

        _scheduler.schedule(new AbstractEBSelfHandling() // runs in the core thread
        {
            @Override
            public void handle_()
            {
                // report
                try {
                    reportEvents_();
                } catch (Exception e) {
                    SVClient.logSendDefectAsync(true, "fail post events from caer to auditor", e);
                } catch (Throwable t) {
                    SystemUtil.fatal("caught unhandled throwable:" + t);
                }

                // reschedule
                synchronized (this) {
                    if (running) {
                        scheduleReport_(Audit.AUDIT_POSTING_INTERVAL);
                    }
                }
            }
        }, runAfter);
    }

    private void reportEvents_()
            throws Exception
    {
        // POST events to the auditor
        long initialActivityLogIndex = lastActivityLogIndex;
        while (true) {
            // create a list of events to send to the auditor
            EventBatch eventBatch = createAuditableEventBatch_(initialActivityLogIndex);

            // bail early if there are no events to report
            if (!eventBatch.hasEvents()) {
                l.debug("no events to report to auditor");
                break;
            }

            // have events, will report
            reportToAuditor_(eventBatch);

            // check if we can still report events or whether we should wait for the next run
            if (!continueIterating(initialActivityLogIndex, eventBatch.lastActivityLogIndex)) {
                l.debug("terminate run - exceeded {} events", MAX_ACTIVITY_LOG_EVENTS_TO_ITERATE_OVER_ON_EACH_RUN);
                break;
            }
        }
    }

    private static boolean continueIterating(long initialActivityLogIndex, long lastActivityLogIndexIndex)
    {
        return ((lastActivityLogIndexIndex - initialActivityLogIndex) < MAX_ACTIVITY_LOG_EVENTS_TO_ITERATE_OVER_ON_EACH_RUN);
    }

    private EventBatch createAuditableEventBatch_(long initialActivityLogIndex)
            throws SQLException
    {
        IDBIterator<ActivityRow> activityRowIterator = _activityLog.getActivitesAfterIndex_(lastActivityLogIndex);
        try {
            long lastActivityLogIndex = 0;
            List<AuditableEvent> reportableEvents = Lists.newArrayListWithCapacity(AUDIT_EVENT_BATCH_SIZE);

            while (activityRowIterator.next_()
                    && (reportableEvents.size() < AUDIT_EVENT_BATCH_SIZE)
                    && continueIterating(initialActivityLogIndex, lastActivityLogIndex)) {
                ActivityRow row = activityRowIterator.get_();
                boolean isLocalEvent = isLocalEvent(row._type);

                // IMPORTANT: update the last activity log index here
                // because we may bail early if we don't have to report this event
                lastActivityLogIndex = row._idx;

                // we only care about local events where _we_ are the source
                if (isLocalEvent && !isSelfGeneratedEvent(row._dids)) {
                    continue;
                }

                AuditableEvent event = createAuditableEvent(isLocalEvent, row);
                reportableEvents.add(event);
            }

            return new EventBatch(lastActivityLogIndex, reportableEvents);
        } finally {
            activityRowIterator.close_();
        }
    }

    private static boolean isLocalEvent(int type)
    {
        return type <= 15;
    }

    private boolean isSelfGeneratedEvent(Set<DID> sourceDids)
    {
        return (sourceDids.size() == 1 && sourceDids.contains(_localdid));
    }

    private AuditableEvent createAuditableEvent(boolean isLocalEvent, ActivityRow row)
            throws SQLException
    {
        AuditableEvent event = _auditClient.event(AuditTopic.FILE, isLocalEvent ? LOCAL_EVENT_NAME : REMOTELY_REQUESTED_EVENT_NAME);

        // our did
        event.embed("device", _hexEncodedLocalDid);

        // destination device
        if (!isLocalEvent) {
            checkArgument(row._dids.size() == 1, "row idx:%s dids:%s", row._idx, row._dids);
            event.embed("destination_device", row._dids.iterator().next().toStringFormal());
        }

        // path
        PathComponents path = createPathComponents(row._path);
        event.embed("path", path);

        // path to (if it exists)
        if (row._pathTo != null) {
            PathComponents pathTo = createPathComponents(row._pathTo);
            event.embed("path_to", pathTo);
        }

        // soid
        SID sid = _sidxTosid.getLocalOrAbsent_(row._soid.sidx());
        event.embed("soid", new SOID(sid.toStringFormal(), row._soid.oid().toStringFormal()));

        // time (in ISO format, UTC timezone)
        event.embed("event_time", _dateFormat.format(row._time));

        // file operations
        event.embed("operations", mapActivityTypeToString(isLocalEvent, row._type));

        return event;
    }

    private static PathComponents createPathComponents(Path path)
    {
        return new PathComponents(path.sid().toStringFormal(), path.toStringRelative());
    }

    private static Set<String> mapActivityTypeToString(boolean isLocalEvent, int type)
    {
        Set<String> actions = Sets.newHashSet();

        if (isLocalEvent) {
            // locally-generated events can be combined
            if ((type & 0x01) == 0x01) actions.add("create");
            if ((type & 0x02) == 0x02) actions.add("modify");
            if ((type & 0x04) == 0x04) actions.add("move");
            if ((type & 0x08) == 0x08) actions.add("delete");
        } else {
            // remotely-requested events are exclusive
            if ((type ^ 0x10) == 0) {
                actions.add("meta_request");
            } else if ((type ^ 0x12) == 0) {
                actions.add("content_request");
            } else if ((type ^ 0x13) == 0) {
                actions.add("content_completed");
            } else {
                throw new IllegalArgumentException("unexpected action:" + type);
            }
        }

        l.trace(">>>> i:{} t:{}", type, actions);

        return actions;
    }

    private void reportToAuditor_(EventBatch eventBatch)
            throws ExAborted, IOException, ExNoResource, SQLException
    {
        checkArgument(eventBatch.hasEvents(), "no events to report to auditor");

        l.debug("{} events to send in batch", eventBatch.numEvents());

        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "audit reporting");
        try {
            TCB tcb = tk.pseudoPause_("audit reporting");
            try {
                for (AuditableEvent event : eventBatch.reportableEvents) {
                    l.trace("report {} to auditor", event);
                    event.publishBlocking();
                }
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }

        // NOTE: this _must_ run with the core lock held

        // FIXME (AG): waiting until the end of the batch to update the lastPostedActivity database may cause duplicate events to be sent to the audit server
        // NOTE: this can happen if most events can be sent successfully,
        // but an intermediate event fails. This will cause us to exit this
        // method without updating the "lastPosted" database. On a
        // subsequent execution we'll attempt to repost this batch to the auditor.
        // I _could_ take two alternative approaches:
        //     1. Update the database on each successful POST (lots of disk activity)
        //     2. Attempt to update the database even if an exception is thrown (*)
        //
        // (*) I considered this option and rejected it because I would have to
        //     perform this action in a finally block. It's possible that the database
        //     update could fail, causing another exception to be thrown from
        //     within the finally block, which could hide the original exception.

        persistLastReportedActivityLogIndex(eventBatch.lastActivityLogIndex);
    }

    private void persistLastReportedActivityLogIndex(long lastReportedActivityLogIndex)
            throws SQLException
    {
        Trans t = _tm.begin_();
        try {
            _auditDatabase.setLastReportedActivityRow_(lastReportedActivityLogIndex, t);
            lastActivityLogIndex = lastReportedActivityLogIndex;
            t.commit_();
        } finally {
            t.end_();
        }

        l.debug("mark event {} as successfully stored", lastActivityLogIndex);
    }
}
