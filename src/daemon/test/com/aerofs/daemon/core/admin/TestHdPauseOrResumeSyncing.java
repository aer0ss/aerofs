/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.admin.EIPauseOrResumeSyncing;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.FakeIMCExecutor;
import com.aerofs.lib.event.Prio;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestHdPauseOrResumeSyncing extends AbstractTest
{
    private LinkStateService _lss = mock(LinkStateService.class);
    private Token _token = mock(Token.class);
    private TCB _tcb = mock(TCB.class);
    private TC _tc = mock(TC.class);
    private HdPauseOrResumeSyncing _handler;

    @Before
    public void setup()
            throws ExAborted
    {
        when(_tc.acquire_(eq(Cat.UNLIMITED), anyString())).thenReturn(_token);
        when(_token.pseudoPause_(anyString())).thenReturn(_tcb);

        _handler = new HdPauseOrResumeSyncing(_tc, _lss);
    }

    @Test
    public void shouldCorrectlyPauseSyncing()
            throws Exception
    {
        EIPauseOrResumeSyncing event = new EIPauseOrResumeSyncing(true, new FakeIMCExecutor());
        _handler.handle_(event, Prio.LO);

        InOrder inOrder = inOrder(_tc, _token, _tcb, _lss);
        inOrder.verify(_tc).acquire_(eq(Cat.UNLIMITED), anyString());
        inOrder.verify(_token).pseudoPause_(anyString());
        inOrder.verify(_lss).markLinksDown();
        inOrder.verify(_tcb).pseudoResumed_();
        inOrder.verify(_token).reclaim_();
    }

    @Test
    public void shouldCorrectlyResumeSyncing()
            throws Exception
    {
        EIPauseOrResumeSyncing event = new EIPauseOrResumeSyncing(false, new FakeIMCExecutor());
        _handler.handle_(event, Prio.LO);

        InOrder inOrder = inOrder(_tc, _token, _tcb, _lss);
        inOrder.verify(_tc).acquire_(eq(Cat.UNLIMITED), anyString());
        inOrder.verify(_token).pseudoPause_(anyString());
        inOrder.verify(_lss).markLinksUp();
        inOrder.verify(_tcb).pseudoResumed_();
        inOrder.verify(_token).reclaim_();
    }
}
