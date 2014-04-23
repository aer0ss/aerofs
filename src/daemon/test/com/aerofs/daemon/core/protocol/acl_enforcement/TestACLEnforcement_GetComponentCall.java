/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.acl_enforcement;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.protocol.ExSenderHasNoPerm;
import com.aerofs.daemon.core.protocol.class_under_test.GetComponentCallWithMocks;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.sql.SQLException;

import static com.aerofs.daemon.core.protocol.ProtocolTestUtil.newDigestedMessage;
import static com.aerofs.daemon.core.protocol.ProtocolTestUtil.newSOCKID;
import static com.aerofs.daemon.core.protocol.class_under_test.AbstractClassUnderTestWithMocks.SINDEXES;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * See acl.md for definitions of ACL enforcement rules.
 */
public class TestACLEnforcement_GetComponentCall extends AbstractTest
{
    GetComponentCallWithMocks caller = new GetComponentCallWithMocks();
    GetComponentCallWithMocks replier = new GetComponentCallWithMocks();

    SIndex _sidxViewer = SINDEXES[0];
    SOCID _socid = newSOCKID(_sidxViewer).socid();

    @Before
    public void setup()
            throws SQLException
    {
        // Minimum wiring to get things working
        when(caller._nvc.getAllLocalVersions_(any(SOCID.class))).thenReturn(Version.empty());
        when(caller._nvc.getLocalVersion_(any(SOCKID.class))).thenReturn(Version.empty());
        when(replier._ds.isPresent_(new SOCKID(_socid))).thenReturn(true);
    }

    @Test
    public void replier_shouldEnforceRule3()
            throws Exception
    {
        // ACL checking on the caller side is passthrough

        // Setup ACL checking for the caller.
        when(replier._lacl.check_(replier.user(), _sidxViewer, Permissions.EDITOR)).thenReturn(false);

        connectCallerToReplier();

        testEnforcementRuleNumber3();

        verifyReplierSendsNoReply();
    }

    // See package-info.java for information about *_negative tests
    @Test(expected = Throwable.class)
    public void replier_shouldEnforceRule3_negative()
            throws Exception
    {
        connectCallerToReplier();

        testEnforcementRuleNumber3();

        verifyReplierSendsNoReply();
    }

    private void testEnforcementRuleNumber3()
            throws Exception
    {
        try {
            caller._gcc.remoteRequestComponent_(_socid, DID.generate(), mock(Token.class));
            fail();
        } catch (ExSenderHasNoPerm ignored) {}
    }

    @Test
    public void replier_shouldEnforceRule1()
            throws Exception
    {
        // ACL checking on the caller side is passthrough

        // Setup ACL checking for the caller.
        when(replier._lacl.check_(caller.user(), _sidxViewer, Permissions.VIEWER)).thenReturn(false);

        connectCallerToReplier();

        testEnforcementRuleNumber1();

        verifyReplierSendsNoReply();
    }

    // See package-info.java for information about *_negative tests
    @Test(expected = Throwable.class)
    public void replier_shouldEnforceRule1_negative()
            throws Exception
    {
        connectCallerToReplier();

        testEnforcementRuleNumber1();

        verifyReplierSendsNoReply();
    }

    private void testEnforcementRuleNumber1()
            throws Exception
    {
        try {
            caller._gcc.remoteRequestComponent_(_socid, DID.generate(), mock(Token.class));
            fail();
        } catch (ExNoPerm e) {}
    }

    private void connectCallerToReplier()
            throws Exception
    {
        when(caller._rpc.do_(any(DID.class), any(PBCore.class), any(Token.class), anyString()))
                .thenAnswer(new Answer<Object>()
                {
                    @Override
                    public Object answer(InvocationOnMock invocation)
                            throws Throwable
                    {
                        PBCore pb = (PBCore)invocation.getArguments()[1];
                        replier._gcc.processCall_(newDigestedMessage(caller.user(), pb));
                        return null;
                    }
                });
    }

    private void verifyReplierSendsNoReply()
    {
        // verify that the replier sends no reply
        verifyNoMoreInteractions(replier._oss, replier._trl);
    }
}
