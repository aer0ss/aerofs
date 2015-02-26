/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.activity;

import com.aerofs.audit.client.IAuditorClient;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.IActivityLogDatabase.ActivityRow;
import com.aerofs.daemon.lib.db.IAuditDatabase;
import com.aerofs.daemon.lib.db.IDID2UserDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.db.MockDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Sets;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.sql.SQLException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static com.aerofs.daemon.core.activity.ClientAuditEventReporter.AUDIT_EVENT_BATCH_SIZE;
import static com.aerofs.daemon.core.activity.ClientAuditEventReporter.MAX_ACTIVITY_LOG_EVENTS_TO_ITERATE_OVER_ON_EACH_RUN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static org.junit.Assert.assertEquals;

// TODO (AG): verify date format?
// TODO (AG): attempt to inject batch size and num events/publish size
public final class TestClientAuditEventReporter
{
    private static SIndex THE_SINDEX = new SIndex(1);
    private static SID THE_SID = SID.generate();
    private static DID LOCAL_DID = DID.generate();
    private static long EVENT_TIME = System.currentTimeMillis();

    private static class MockActivityLogDBIterator extends MockDBIterator<ActivityRow>
    {
        private static Random RANDOM = new Random();

        private MockActivityLogDBIterator(long startIndex, int numRows)
        {
            this(generateRows(startIndex, numRows));
        }

        private MockActivityLogDBIterator(ActivityRow[] rows)
        {
            super(rows);
        }

        private static ActivityRow[] generateRows(long startIndex, int numRows)
        {
            ActivityRow[] rows = new ActivityRow[numRows];

            for (int i = 0; i < numRows; i++) {
                int activityValue = getRandomActivity().getValue();
                DID sourceDid = ClientActivity.isLocalActivity(activityValue) ? LOCAL_DID : DID.generate();
                rows[i] = newActivityRow(startIndex + i, activityValue, RANDOM.nextBoolean(), sourceDid);
            }

            return rows;
        }

        private static ClientActivity getRandomActivity()
        {
            return ClientActivity.values()[RANDOM.nextInt(ClientActivity.values().length)];
        }
    }

    private static class MockRemoteActivityLogDBIterator extends MockDBIterator<ActivityRow>
    {
        private static Random RANDOM = new Random();

        public MockRemoteActivityLogDBIterator(long startIndex, int numRows)
        {
            this(generateRows(startIndex, numRows));
        }

        private MockRemoteActivityLogDBIterator(ActivityRow[] rows)
        {
            super(rows);
        }

        private static ActivityRow[] generateRows(long startIndex, int numRows)
        {
            ActivityRow[] rows = new ActivityRow[numRows];

            for (int i = 0; i < numRows; i++) {
                rows[i] = newActivityRow(startIndex + i,
                        ClientActivity.CONTENT_COMPLETED.getValue(), RANDOM.nextBoolean(),
                        DID.generate());
            }

            return rows;
        }
    }

    private static ActivityRow newActivityRow(long rowIndex, int activityValue,
            boolean hasParentPath, DID sourceDid)
    {
        return new ActivityRow(
                rowIndex,
                new SOID(THE_SINDEX, OID.generate()),
                activityValue,
                new Path(THE_SID, "folder1", "folder1-1"),
                hasParentPath ? new Path(THE_SID, "folder2") : null,
                Sets.<DID>newHashSet(sourceDid),
                EVENT_TIME);
    }

    private final CfgLocalDID _cfgLocalDID = mock(CfgLocalDID.class);
    private final TokenManager _tokenManager = mock(TokenManager.class);
    private final Token _tk = mock(Token.class);
    private final TCB _tcb = mock(TCB.class);
    private final CoreScheduler _scheduler = mock(CoreScheduler.class);
    private final Trans _trans = mock(Trans.class);
    private final TransManager _tm = mock(TransManager.class);
    private final IMapSIndex2SID _sidxToSid = mock(IMapSIndex2SID.class);
    private final ActivityLog _al = mock(ActivityLog.class);
    private final IAuditorClient _auditorClient = mock(IAuditorClient.class);
    private final IAuditDatabase _auditDatabase = mock(IAuditDatabase.class);
    private final UserAndDeviceNames _udinfo = mock(UserAndDeviceNames.class);
    private final IDID2UserDatabase _did2user = mock(IDID2UserDatabase.class);

    private ClientAuditEventReporter _caer; // sut

    @Before
    public void setup()
            throws ExNoResource, SQLException, ExAborted
    {
        when(_cfgLocalDID.get()).thenReturn(LOCAL_DID);
        when(_tokenManager.acquire_(any(Cat.class), any(String.class))).thenReturn(_tk);
        when(_tk.pseudoPause_(anyString())).thenReturn(_tcb);
        when(_tm.begin_()).thenReturn(_trans);
        when(_sidxToSid.getLocalOrAbsent_(THE_SINDEX)).thenReturn(THE_SID);

        _caer = new ClientAuditEventReporter(
                _cfgLocalDID,
                _tokenManager,
                _scheduler,
                _tm,
                _sidxToSid,
                _al,
                _did2user,
                _auditorClient,
                _auditDatabase);
    }

    @Test
    public void shouldLoadCorrectActivityLogRowOnInitialization()
            throws SQLException
    {
        long lastActivityLogIndex = 82;

        when(_auditDatabase.getLastReportedActivityRow_()).thenReturn(lastActivityLogIndex);

        _caer.init_();

        assertThat(_caer.getLastActivityLogIndex_(), equalTo(lastActivityLogIndex));
    }

    @Test
    public void shouldNotReportAnyEventsIfTheActivityLogTableIsEmpty()
            throws Exception
    {
        long lastActivityLogIndex = 0;

        when(_auditDatabase.getLastReportedActivityRow_()).thenReturn(lastActivityLogIndex);
        when(_al.getActivitesAfterIndex_(lastActivityLogIndex)).thenReturn(new MockActivityLogDBIterator(0, 0));

        _caer.init_();

        _caer.reportEventsForUnitTestOnly_();

        verifyNoMoreInteractions(_auditorClient);
    }

    @Test
    public void shouldNotReportAnyEventsIfAllRowsInTheActivityLogTableHaveBeenReportedAlready()
            throws Exception
    {
        long lastActivityLogIndex = 10;

        when(_auditDatabase.getLastReportedActivityRow_()).thenReturn(lastActivityLogIndex);
        when(_al.getActivitesAfterIndex_(lastActivityLogIndex)).thenReturn(new MockActivityLogDBIterator(0, 0));

        _caer.init_();

        _caer.reportEventsForUnitTestOnly_();

        verifyNoMoreInteractions(_auditorClient);

        // we shouldn't update our database
        InOrder auditDatabaseOrder = inOrder(_auditDatabase);
        auditDatabaseOrder.verify(_auditDatabase).getLastReportedActivityRow_();
        auditDatabaseOrder.verifyNoMoreInteractions();

        // or the internal state
        assertThat(_caer.getLastActivityLogIndex_(), equalTo(lastActivityLogIndex));
    }

    // this test also verifies that we update the audit database properly
    @Test
    public void shouldReportActivityLogEventsAddedAfterTheLastReportedActivityLogIndex()
            throws Exception
    {
        int numNewActivityLogRows = 4;
        long startActivityLogIndex = 10;
        long finitActivityLogIndex = startActivityLogIndex + numNewActivityLogRows;

        // pretend that there are 4 new rows to be updated
        when(_auditDatabase.getLastReportedActivityRow_()).thenReturn(startActivityLogIndex);
        when(_al.getActivitesAfterIndex_(startActivityLogIndex)).thenReturn(new MockActivityLogDBIterator(11, numNewActivityLogRows));
        when(_al.getActivitesAfterIndex_(finitActivityLogIndex)).thenReturn(new MockActivityLogDBIterator(15, 0));

        _caer.init_();

        _caer.reportEventsForUnitTestOnly_();

        // verify that we publish 4 times
        verify(_auditorClient, times(numNewActivityLogRows)).submit(notNull(String.class));
        verifyNoMoreInteractions(_auditorClient);

        // and that we update the audit database
        InOrder auditDatabaseOrder = inOrder(_auditDatabase);
        auditDatabaseOrder.verify(_auditDatabase).getLastReportedActivityRow_();
        auditDatabaseOrder.verify(_auditDatabase).setLastReportedActivityRow_(finitActivityLogIndex, _trans);
        auditDatabaseOrder.verifyNoMoreInteractions();

        // just a final check of the internal state
        assertThat(_caer.getLastActivityLogIndex_(), equalTo(finitActivityLogIndex));
    }

    // this test also verifies that we update the audit database properly
    @Test
    public void shouldNotSendMoreThanMaxBatchSizeActivityLogEventsInEachReportEventsCall()
            throws Exception
    {
        int numNewActivityLogRows = MAX_ACTIVITY_LOG_EVENTS_TO_ITERATE_OVER_ON_EACH_RUN;
        long startActivityLogIndex = 0;
        long finitActivityLogIndex = startActivityLogIndex + numNewActivityLogRows;

        // pretend that there are many new rows to be updated
        // NOTE: there should only be _4_ calls to _al because we should bail after the max is reached
        when(_auditDatabase.getLastReportedActivityRow_()).thenReturn(startActivityLogIndex);
        when(_al.getActivitesAfterIndex_(  0)).thenReturn(new MockActivityLogDBIterator(  1, AUDIT_EVENT_BATCH_SIZE));
        when(_al.getActivitesAfterIndex_( 50)).thenReturn(new MockActivityLogDBIterator( 51, AUDIT_EVENT_BATCH_SIZE));
        when(_al.getActivitesAfterIndex_(100)).thenReturn(new MockActivityLogDBIterator(101, AUDIT_EVENT_BATCH_SIZE));
        when(_al.getActivitesAfterIndex_(150)).thenReturn(new MockActivityLogDBIterator(151, AUDIT_EVENT_BATCH_SIZE));

        _caer.init_();

        _caer.reportEventsForUnitTestOnly_();

        // verify that we publish the maximum number of times
        verify(_auditorClient, times(MAX_ACTIVITY_LOG_EVENTS_TO_ITERATE_OVER_ON_EACH_RUN)).submit(notNull(String.class));
        verifyNoMoreInteractions(_auditorClient);

        // and that we update the audit database
        InOrder auditDatabaseOrder = inOrder(_auditDatabase);
        auditDatabaseOrder.verify(_auditDatabase).getLastReportedActivityRow_();
        auditDatabaseOrder.verify(_auditDatabase).setLastReportedActivityRow_( 50, _trans);
        auditDatabaseOrder.verify(_auditDatabase).setLastReportedActivityRow_(100, _trans);
        auditDatabaseOrder.verify(_auditDatabase).setLastReportedActivityRow_(150, _trans);
        auditDatabaseOrder.verify(_auditDatabase).setLastReportedActivityRow_(200, _trans);
        auditDatabaseOrder.verifyNoMoreInteractions();

        // just a final check of the internal state
        assertThat(_caer.getLastActivityLogIndex_(), equalTo(finitActivityLogIndex));
    }

    // this situation can happen if there are a bunch of local events
    // but all of them are generated by a remote DID
    @Test
    public void shouldContinueSendingEventsEvenIfTheFirstEventBatchIsEmpty()
        throws Exception
    {
        int numNewActivityLogRows = 175;
        assertThat(numNewActivityLogRows , lessThan(MAX_ACTIVITY_LOG_EVENTS_TO_ITERATE_OVER_ON_EACH_RUN));

        long startActivityLogIndex = 0;
        long finitActivityLogIndex = startActivityLogIndex + numNewActivityLogRows;

        when(_auditDatabase.getLastReportedActivityRow_()).thenReturn(startActivityLogIndex);

        // NOTE: all the rows are _remotely triggered_ local events, which means they should not get posted

        // first batch
        ActivityRow[] batch0Rows = createRemotelyTriggeredLocalActivityRows(1, AUDIT_EVENT_BATCH_SIZE);
        when(_al.getActivitesAfterIndex_(  0)).thenReturn(new MockActivityLogDBIterator(batch0Rows));

        // second batch
        ActivityRow[] batch1Rows = createRemotelyTriggeredLocalActivityRows(51, AUDIT_EVENT_BATCH_SIZE);
        when(_al.getActivitesAfterIndex_( 50)).thenReturn(new MockActivityLogDBIterator(batch1Rows));

        // third batch
        ActivityRow[] batch2Rows = createRemotelyTriggeredLocalActivityRows(101, AUDIT_EVENT_BATCH_SIZE);
        when(_al.getActivitesAfterIndex_(100)).thenReturn(new MockActivityLogDBIterator(batch2Rows));

        // fourth batch
        ActivityRow[] batch3Rows = createRemotelyTriggeredLocalActivityRows(151, 25); // only 25 here
        when(_al.getActivitesAfterIndex_(150)).thenReturn(new MockActivityLogDBIterator(batch3Rows));

        // we'll be called one last time...
        // this time, return nothing
        when(_al.getActivitesAfterIndex_(175)).thenReturn(new MockActivityLogDBIterator(176, 0));

        _caer.init_();

        _caer.reportEventsForUnitTestOnly_();

        // verify that we don't publish at all (because there are no events to report)
        verifyNoMoreInteractions(_auditorClient);

        // _but_ we should always update the epoch number
        InOrder auditDatabaseOrder = inOrder(_auditDatabase);
        auditDatabaseOrder.verify(_auditDatabase).getLastReportedActivityRow_();
        auditDatabaseOrder.verify(_auditDatabase).setLastReportedActivityRow_( 50, _trans);
        auditDatabaseOrder.verify(_auditDatabase).setLastReportedActivityRow_(100, _trans);
        auditDatabaseOrder.verify(_auditDatabase).setLastReportedActivityRow_(150, _trans);
        auditDatabaseOrder.verify(_auditDatabase).setLastReportedActivityRow_(175, _trans);
        // notice that once 175 is set it's never set again!
        // this is because we break early if there are no rows left
        auditDatabaseOrder.verifyNoMoreInteractions();

        // just a final check of the internal state
        assertThat(_caer.getLastActivityLogIndex_(), equalTo(finitActivityLogIndex));
    }

    private ActivityRow[] createRemotelyTriggeredLocalActivityRows(int startRowIndex, int numRows)
    {
        ActivityRow[] rows = new ActivityRow[numRows];

        for (int i = 0; i < numRows; i++) {
            rows[i] = newActivityRow(startRowIndex + i, ClientActivity.CREATE.getValue(), false, DID.generate());
        }

        return rows;
    }

    @Test
    public void shouldNotUpdateAuditDatabaseWhenActivityLogEventsAreNotSuccessfullyPosted()
            throws Exception
    {
        final IllegalStateException auditorException = new IllegalStateException();
        final long startActivityLogIndex = 0;

        // pretend there are 4 rows to update
        when(_auditDatabase.getLastReportedActivityRow_()).thenReturn(startActivityLogIndex);
        when(_al.getActivitesAfterIndex_(startActivityLogIndex)).thenReturn(new MockActivityLogDBIterator(1, 4));

        // pretend we have a failing client
        doAnswer(new Answer<Void>()
        {
            int numCalls = 0;

            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                if (numCalls == 2) {
                    throw auditorException;
                } else {
                    numCalls++;
                }

                return null;
            }
        }).when(_auditorClient).submit(anyString());

        _caer.init_();

        AtomicReference<Exception> thrown = new AtomicReference<>(null);
        try {
            _caer.reportEventsForUnitTestOnly_();
        } catch (Exception e) {
            thrown.set(e);
        }
        assertThat(thrown.get(), notNullValue());
        assertThat(thrown.get(), Matchers.<Exception>is(auditorException));

        // verify that we tried to publish a few times before the actual failing call
        verify(_auditorClient, times(3)).submit(notNull(String.class));
        verifyNoMoreInteractions(_auditorClient);

        // and that we simply read from the database, but never update it
        InOrder auditDatabaseOrder = inOrder(_auditDatabase);
        auditDatabaseOrder.verify(_auditDatabase).getLastReportedActivityRow_();
        auditDatabaseOrder.verifyNoMoreInteractions();

        // just a final check of the internal state
        assertThat(_caer.getLastActivityLogIndex_(), equalTo(startActivityLogIndex));
    }

    // TODO (MP) re-enable later.
    //@Test
    public void shouldResolveDestinationUserIDForFileTransferEvents()
            throws Exception
    {
        int numNewActivityLogRows = 1;
        long startActivityLogIndex = 0;
        long finitActivityLogIndex = startActivityLogIndex + numNewActivityLogRows;

        when(_auditDatabase.getLastReportedActivityRow_())
                .thenReturn(startActivityLogIndex);
        when(_al.getActivitesAfterIndex_(startActivityLogIndex))
                .thenReturn(new MockRemoteActivityLogDBIterator(1, numNewActivityLogRows));
        when(_al.getActivitesAfterIndex_(finitActivityLogIndex))
                .thenReturn(new MockRemoteActivityLogDBIterator(2, 0));
        when(_udinfo.getDeviceOwnerNullable_(any(DID.class)))
                .thenReturn(UserID.fromExternal("user@acme.com"));

        _caer.init_();
        _caer.reportEventsForUnitTestOnly_();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(_auditorClient).submit(captor.capture());

        List<String> args = captor.getAllValues();
        assertEquals(1, args.size());
        assertTrue(args.get(0).contains("\"destination_user\":\"user@acme.com\""));

        // just a final check of the internal state
        assertThat(_caer.getLastActivityLogIndex_(), equalTo(finitActivityLogIndex));
    }

    // TODO (MP) re-enable later.
    //@Test
    public void shouldInsertEmptyTagForFileTransferEvents()
            throws Exception
    {
        int numNewActivityLogRows = 1;
        long startActivityLogIndex = 0;
        long finitActivityLogIndex = startActivityLogIndex + numNewActivityLogRows;

        when(_auditDatabase.getLastReportedActivityRow_())
                .thenReturn(startActivityLogIndex);
        when(_al.getActivitesAfterIndex_(startActivityLogIndex))
                .thenReturn(new MockRemoteActivityLogDBIterator(1, numNewActivityLogRows));
        when(_al.getActivitesAfterIndex_(finitActivityLogIndex))
                .thenReturn(new MockRemoteActivityLogDBIterator(2, 0));

        _caer.init_();
        _caer.reportEventsForUnitTestOnly_();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(_auditorClient).submit(captor.capture());

        List<String> args = captor.getAllValues();
        assertEquals(1, args.size());
        assertFalse(args.get(0).contains("\"destination_user\":\"user@acme.com\""));

        // just a final check of the internal state
        assertThat(_caer.getLastActivityLogIndex_(), equalTo(finitActivityLogIndex));
    }
}
