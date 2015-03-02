/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.acl_enforcement;

import com.aerofs.base.acl.Permissions;
import com.aerofs.ids.DID;
import com.aerofs.daemon.core.protocol.class_under_test.GetVersionsRequestWithMocks;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.SIndex;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.sql.SQLException;

import static com.aerofs.daemon.core.protocol.ProtocolTestUtil.newDigestedMessage;
import static com.aerofs.daemon.core.protocol.class_under_test.AbstractClassUnderTestWithMocks.SINDEXES;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * See acl.md for definitions of ACL enforcement rules.
 */
public class TestACLEnforcement_GetVersionsRequest extends AbstractTest
{
    GetVersionsRequestWithMocks caller = new GetVersionsRequestWithMocks();
    GetVersionsRequestWithMocks replier = new GetVersionsRequestWithMocks();

    SIndex _sidxViewer = SINDEXES[0];

    @Before
    public void setup()
            throws SQLException
    {
        // Minimum wiring to get things working
        when(caller._nvc.getKnowledgeExcludeSelf_(any(SIndex.class))).thenReturn(Version.empty());
        when(caller._ivc.getKnowledgeExcludeSelf_(any(SIndex.class))).thenReturn(Version.empty());
    }

    @Test
    public void replier_shouldEnforceRule1()
            throws Exception
    {
        // Setup ACL checking for the replier.
        when(replier._lacl.check_(caller.user(), _sidxViewer, Permissions.VIEWER)).thenReturn(false);

        // ACL checking on the caller side is passthrough

        connectAndRunAndVerify();
    }

    @Test
    public void replier_shouldEnforceRule3()
            throws Exception
    {
        // Setup ACL checking for the replier.
        when(replier._lacl.check_(replier.user(), _sidxViewer, Permissions.EDITOR)).thenReturn(false);

        // ACL checking on the caller side is passthrough

        connectAndRunAndVerify();
    }

    // See package-info.java for information about *_negative tests
    @Test(expected = Throwable.class)
    public void replier_shouldEnforceRule1_and_rule3_negative()
            throws Exception
    {
        connectAndRunAndVerify();
    }

    private void connectAndRunAndVerify()
            throws Exception
    {
        connectCallerToReplier();

        caller._gvc.issueRequest_(DID.generate(), _sidxViewer);

        // Verify the replier sends no version data. Because the replier always sends a reply, it's
        // easier to verify that the replier doesn't interact with the local version subsystem than
        // to verify the replied message doesn't contain actual version data.
        verifyNoMoreInteractions(replier._nvc, replier._ivc, replier._sidx2contrib, replier._sidx2s);
    }

    private void connectCallerToReplier()
            throws Exception
    {
        when(caller._trl.sendUnicast_(any(DID.class), any(String.class), anyInt(),
                any(ByteArrayOutputStream.class))).thenAnswer(invocation -> {
                    ByteArrayOutputStream os = (ByteArrayOutputStream)invocation.getArguments()[3];
                    replier._gvc.handle_(newDigestedMessage(caller.user(), os));
                    return null;
                });
    }
}
