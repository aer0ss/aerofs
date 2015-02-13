/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.acl_enforcement;

import com.aerofs.base.acl.Permissions;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.protocol.class_under_test.NewUpdatesWithMocks;
import com.aerofs.lib.Tick;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.util.Set;

import static com.aerofs.daemon.core.protocol.ProtocolTestUtil.*;
import static com.aerofs.daemon.core.protocol.class_under_test.AbstractClassUnderTestWithMocks.SINDEXES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * See acl.md for definitions of ACL enforcement rules.
 */
public class TestACLEnforcement_NewUpdates extends AbstractTest
{
    NewUpdatesWithMocks sender = new NewUpdatesWithMocks();
    NewUpdatesWithMocks receiver = new NewUpdatesWithMocks();

    @Captor ArgumentCaptor<SIndex> captor;

    SIndex _sidxViewer = SINDEXES[0];

    Set<SIndex> _sidxs = Sets.newHashSet();
    {
        _sidxs.add(_sidxViewer);
        _sidxs.add(SINDEXES[1]);
        _sidxs.add(SINDEXES[2]);
    }

    @Before
    public void setup()
            throws SQLException
    {
        // Minimum wiring to get things working
        when(sender._nvc.getLocalTickNullable_(any(SOCKID.class))).thenReturn(Tick.ZERO);
    }

    @Test
    public void sender_shouldEnforceRule3()
            throws Exception
    {
        // Setup ACL checking for the replier.
        when(sender._lacl.check_(sender.user(), _sidxViewer, Permissions.EDITOR)).thenReturn(false);

        // ACL checking on the caller side is passthrough

        connectAndRunAndVerify();
    }

    @Test
    public void receiver_shouldEnforceRule2()
            throws Exception
    {
        // ACL checking on the replier side is passthrough

        // Setup ACL checking for the caller.
        when(receiver._lacl.check_(sender.user(), _sidxViewer, Permissions.EDITOR)).thenReturn(false);

        connectAndRunAndVerify();
    }

    // See package-info.java for information about *_negative tests
    @Test(expected = Throwable.class)
    public void sender_shouldEnforceRule3_and_receiver_shouldEnforceRule2_negative()
            throws Exception
    {
        connectAndRunAndVerify();
    }

    private void connectAndRunAndVerify()
            throws Exception
    {
        connectSenderToReceiver();
        sender._nu.send_(_sidxs);
        verifyResult();
    }

    private void connectSenderToReceiver()
            throws Exception
    {
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                ByteArrayOutputStream os = (ByteArrayOutputStream) invocation.getArguments()[3];
                receiver._nu.process_(newDigestedMessage(sender.user(), os));
                return null;
            }
        }).when(sender._trl).sendMaxcast_(any(SID.class), anyString(), anyInt(),
                any(ByteArrayOutputStream.class));
    }


    private void verifyResult()
            throws SQLException
    {
        // Capture the values
        // 2 is the number of read-write stores in _sidxs
        verify(receiver._ae, times(2)).request_(captor.capture(), any(DID.class));

        // Verify the caller gets all the components of read-write stores from the replier
        Set<SIndex> set = Sets.newHashSet(_sidxs);
        for (SIndex sidx : captor.getAllValues()) assertTrue(set.remove(sidx));

        // Verify the caller gets no componets of the read-only store from the replier. 1 is the
        // number of read-only stores in _sidxs
        assertEquals(set.size(), 1);
        for (SIndex sidx : set) assertEquals(sidx, _sidxViewer);
    }
}
