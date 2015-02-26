/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.status.PauseSync;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.admin.EIPauseOrResumeSyncing;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.lib.event.Prio;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestHdPauseOrResumeSyncing extends AbstractTest
{
    private @Mock LinkStateService _lss;
    private @Mock PauseSync pause;
    private @Mock Token _token;
    private @Mock TCB _tcb;
    private @Mock TokenManager _tokenManager;
    private @Mock IIMCExecutor _imce;

    private @InjectMocks HdPauseOrResumeSyncing _handler;

    @Before
    public void setup() throws Exception
    {
        when(_tokenManager.acquire_(eq(Cat.UNLIMITED), anyString())).thenReturn(_token);
        when(_token.pseudoPause_(anyString())).thenReturn(_tcb);
    }

    @Test
    public void shouldCorrectlyPauseSyncing()
            throws Exception
    {
        EIPauseOrResumeSyncing event = new EIPauseOrResumeSyncing(true, _imce);
        _handler.handle_(event, Prio.LO);

        verify(pause).pause();
        InOrder inOrder = inOrder(_tokenManager, _token, _tcb, _lss);
        inOrder.verify(_tokenManager).acquire_(eq(Cat.UNLIMITED), anyString());
        inOrder.verify(_token).pseudoPause_(anyString());
        inOrder.verify(_lss).markLinksDown();
        inOrder.verify(_tcb).pseudoResumed_();
        inOrder.verify(_token).reclaim_();
    }

    @Test
    public void shouldCorrectlyResumeSyncing()
            throws Exception
    {
        EIPauseOrResumeSyncing event = new EIPauseOrResumeSyncing(false, _imce);
        _handler.handle_(event, Prio.LO);

        verify(pause).resume();
        InOrder inOrder = inOrder(_tokenManager, _token, _tcb, _lss);
        inOrder.verify(_tokenManager).acquire_(eq(Cat.UNLIMITED), anyString());
        inOrder.verify(_token).pseudoPause_(anyString());
        inOrder.verify(_lss).markLinksUp();
        inOrder.verify(_tcb).pseudoResumed_();
        inOrder.verify(_token).reclaim_();
    }
}
