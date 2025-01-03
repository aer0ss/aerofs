/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.activity;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.audit.client.AuditClient.AuditableEvent;
import com.aerofs.audit.client.IAuditorClient;
import com.aerofs.base.AuditParam;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.IActivityLogDatabase.ActivityRow;
import com.aerofs.daemon.lib.db.IAuditDatabase;
import com.aerofs.daemon.lib.db.IDID2UserDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.sched.Scheduler;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static com.aerofs.defects.Defects.newDefectWithLogs;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;

/**
 * Service that reads events from the local
 * {@link com.aerofs.daemon.core.activity.ActivityLog} and
 * reports them to the centralized audit service.
 * <p/>
 * This implementation posts events to the auditor
 * every {@link com.aerofs.base.AuditParam#_interval}
 * milliseconds.
 */
public final class ClientAuditEventReporter implements IClientAuditEventReporter
{
    public static final int AUDIT_EVENT_BATCH_SIZE = 50;
    public static final int MAX_ACTIVITY_LOG_EVENTS_TO_ITERATE_OVER_ON_EACH_RUN = 200;

    private static final String LOCAL_EVENT_NAME = "file.notification";
    private static final String REMOTELY_REQUESTED_EVENT_NAME = "file.transfer";

    private static final Logger l = LoggerFactory.getLogger(ClientAuditEventReporter.class);

    private final DID _localdid;
    private final String _hexEncodedLocalDid;
    private final TokenManager _tokenManager;
    private final Scheduler _scheduler;
    private final TransManager _tm;
    private final IAuditDatabase _auditDatabase;
    private final ActivityLog _activityLog;
    private final IMapSIndex2SID _sidxTosid;
    private final IDID2UserDatabase _did2user;
    private final SimpleDateFormat _dateFormat;
    private final AuditClient _auditClient = new AuditClient();
    private final AuditParam _param;

    private boolean running = false;
    private long lastActivityLogIndex;

    //---------------------------------
    // GSON objects
    //

    private static class PathComponents
    {
        final String sid;
        final String relativePath;

        private PathComponents(Path path)
        {
            this.sid = path.sid().toStringFormal();
            this.relativePath = path.toStringRelative();
        }
    }

    private static class SIDOID
    {
        final String sid;
        final String oid;

        private SIDOID(SID sid, OID oid)
        {
            this.sid = sid.toStringFormal();
            this.oid = oid.toStringFormal();
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
            IMapSIndex2SID sidxTosid,
            ActivityLog activityLog,
            IDID2UserDatabase did2user,
            IAuditorClient auditorClient,
            IAuditDatabase auditDatabase,
            AuditParam param)
    {
        _localdid = localdid.get();
        _hexEncodedLocalDid = localdid.get().toStringFormal();
        _tokenManager = tokenManager;
        _scheduler = scheduler;
        _tm = tm;
        _auditDatabase = auditDatabase;
        _activityLog = activityLog;
        _did2user = did2user;
        _sidxTosid = sidxTosid;
        _dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        _dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        _auditClient.setAuditorClient(auditorClient);
        _param = param;
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
     * {@link com.aerofs.base.AuditParam#_initialDelay}
     * milliseconds after this method is called. Subsequent reports are
     * scheduled every {@link com.aerofs.base.AuditParam#_initialDelay}
     * milliseconds.
     * <p/>
     * Once {@code start_} is called, subsequent calls are noops.
     */
    public synchronized void start_()
    {
        l.info("starting caer");

        if (running) return;

        running = true;

        scheduleReport_(_param._initialDelay);
    }

    /**
     * Report auditable events to the configured {@link com.aerofs.audit.client.AuditClient}.
     * <p/>
     * <strong>IMPORTANT:</strong> This call is meant to be used in
     * a unit test context only. It should <strong>NEVER</strong> be
     * used in any other situation.
     *
     * @throws java.lang.Exception if auditable events could not be generated or reported
     */
    void reportEventsForUnitTestOnly_()
            throws Exception
    {
        reportEvents_();
    }

    /**
     * Get the index of the last activity log row successfully posted to the auditor.
     *
     * @return integer >=0 of the last activity log row successfully posted to the auditor. If
     * 0 is returned, this means that no events have been posted to the auditor.
     */
    long getLastActivityLogIndex_()
    {
        return lastActivityLogIndex;
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
                } catch (Throwable t) {
                    // having auditing cause a daemon crash loop is unacceptable
                    l.error("unhandled exception in caer", t);
                    newDefectWithLogs("audit.client.schedule_report")
                            .setMessage("fail post events from caer to auditor")
                            .setException(t)
                            .sendAsync();
                }

                // reschedule
                synchronized (this) {
                    if (running) {
                        scheduleReport_(_param._interval);
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

            // have events, will report
            if (eventBatch.hasEvents()) {
                reportToAuditor_(eventBatch);
            }

            // were there any events left in the db?
            if (eventBatch.lastActivityLogIndex == lastActivityLogIndex) {
                l.debug("terminate run - no events in al");
                break;
            }

            // we always have to indicate that we've moved our position in the al table
            persistLastReportedActivityLogIndex_(eventBatch.lastActivityLogIndex);

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
            throws SQLException, ExProtocolError
    {
        IDBIterator<ActivityRow> activityRowIterator = _activityLog.getActivitesAfterIndex_(lastActivityLogIndex);
        try {
            long lastIteratedActivityLogIndex = lastActivityLogIndex;
            List<AuditableEvent> reportableEvents = Lists.newArrayListWithCapacity(AUDIT_EVENT_BATCH_SIZE);

            while (activityRowIterator.next_()
                    && (reportableEvents.size() < AUDIT_EVENT_BATCH_SIZE)
                    && continueIterating(initialActivityLogIndex, lastIteratedActivityLogIndex)) {
                ActivityRow row = activityRowIterator.get_();
                boolean isLocalEvent = ClientActivity.isLocalActivity(row._type);

                // IMPORTANT: update the last activity log index here
                // because we may bail early if we don't have to report this event
                lastIteratedActivityLogIndex = row._idx;

                // we only care about local events where _we_ are the source
                if (isLocalEvent && !isSelfGeneratedEvent(row._dids)) {
                    continue;
                }

                AuditableEvent event = createAuditableEvent_(isLocalEvent, row);
                reportableEvents.add(event);
            }

            return new EventBatch(lastIteratedActivityLogIndex, reportableEvents);
        } finally {
            activityRowIterator.close_();
        }
    }

    private boolean isSelfGeneratedEvent(Set<DID> sourceDids)
    {
        // self-generated events include those made on behalf of a mobile device
        // NB: when expelling and re-admitting it is possible to get a modify event with
        // multiple devices that include the local device. These should NOT be treated as
        // self-generated events
        return sourceDids.contains(_localdid)
                && (sourceDids.size() == 1
                         || (sourceDids.size() == 2 && otherDevice(sourceDids).isMobileDevice()));
    }

    private DID otherDevice(Set<DID> dids)
    {
        checkArgument(dids.size() == 2);
        checkArgument(dids.contains(_localdid));
        DID d0 = Iterables.get(dids, 0);
        return d0.equals(_localdid) ? Iterables.get(dids, 1) : d0;
    }

    private AuditableEvent createAuditableEvent_(boolean isLocalEvent, ActivityRow row)
            throws SQLException, ExProtocolError
    {
        AuditableEvent event = _auditClient.event(AuditTopic.FILE, isLocalEvent ? LOCAL_EVENT_NAME : REMOTELY_REQUESTED_EVENT_NAME);

        // our did
        event.embed("device", _hexEncodedLocalDid);

        // destination device
        if (!isLocalEvent) {
            checkArgument(row._dids.size() == 1, "row idx:%s dids:%s", row._idx, row._dids);

            DID destinationDevice = getOnlyElement(row._dids);
            event.embed("destination_device", destinationDevice.toStringFormal());

            // Remote events with unresolvable destination users should be very rare, because such
            // events only occur if file transfer happens immediately before shared folder user
            // removal, or if other similar race conditions happen, AND we experience a local DID
            // to user cache miss. In this case we simply leave the destination user key empty in
            // the event.

            UserID destUser = _did2user.getNullable_(destinationDevice);
            event.embed("destination_user", destUser != null ? destUser.getString() : "");
        } else if (row._dids.size() == 2) {
            // on behalf of mobile device
            DID mdid = otherDevice(row._dids);
            checkArgument(mdid.isMobileDevice());
            UserID mobileUser = _did2user.getNullable_(mdid);
            event.embed("mobile_device", mdid.toStringFormal());
            event.embed("mobile_user", mobileUser != null ? mobileUser.getString() : "");
        }

        // path
        event.embed("path", new PathComponents(row._path));

        // path to (if it exists)
        if (row._pathTo != null) {
            event.embed("path_to", new PathComponents(row._pathTo));
        }

        // soid
        SID sid = _sidxTosid.getLocalOrAbsent_(row._soid.sidx());
        event.embed("soid", new SIDOID(sid, row._soid.oid()));

        // time (in ISO format, UTC timezone)
        event.embed("event_time", _dateFormat.format(row._time));

        // file operations
        event.embed("operations", ClientActivity.getIndicatedActivities(row._type));

        return event;
    }

    private void reportToAuditor_(EventBatch eventBatch)
            throws ExAborted, IOException, ExNoResource, SQLException
    {
        checkArgument(eventBatch.hasEvents(), "no events to report to auditor");

        l.debug("{} events to send in batch", eventBatch.numEvents());

        _tokenManager.inPseudoPause_(Cat.UNLIMITED, "audit reporting", () -> {
            for (AuditableEvent event : eventBatch.reportableEvents) {
                event.publishBlocking();
            }
            return null;
        });
    }

    private void persistLastReportedActivityLogIndex_(long lastReportedActivityLogIndex)
            throws SQLException
    {
        try (Trans t = _tm.begin_()) {
            _auditDatabase.setLastReportedActivityRow_(lastReportedActivityLogIndex, t);
            lastActivityLogIndex = lastReportedActivityLogIndex;
            t.commit_();
        }
        l.debug("mark event {} as successfully stored", lastActivityLogIndex);
    }

    public static class Noop implements IClientAuditEventReporter
    {
        @Override
        public void init_() throws Exception
        {

        }

        @Override
        public void start_()
        {
            l.info("Auditing is disabled. Use no-op CAER.");
        }
    }
}
