/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.linker;

import com.aerofs.daemon.core.linker.event.EIMightCreateNotification;
import com.aerofs.daemon.core.linker.scanner.ScanSessionQueue;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TestHdMightCreateNotification extends AbstractTest
{
    @Mock MightCreate mc;
    @Mock ScanSessionQueue ssq;
    @Mock TransManager tm;
    @Mock CfgAbsRootAnchor cfgAbsRootAnchor;

    @InjectMocks HdMightCreateNotification mcn;

    EIMightCreateNotification ev = new EIMightCreateNotification(Util.join("root", "test"));

    @Before
    public void setup()
    {
        when(cfgAbsRootAnchor.get()).thenReturn("root");
        when(tm.begin_()).then(RETURNS_MOCKS);
    }

    @Test
    public void shouldIgnoreExNotFoundFromMightCreate()
            throws Exception
    {
        when(mc.mightCreate_(any(PathCombo.class), any(IDeletionBuffer.class), any(Trans.class)))
                .thenThrow(new ExNotFound());

        callHandle();

        verifyZeroInteractions(ssq);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldTriggerFullScanOnOtherExceptionsFromMightCreate()
            throws Exception
    {
        when(mc.mightCreate_(any(PathCombo.class), any(IDeletionBuffer.class), any(Trans.class)))
                .thenThrow(new ExNoPerm());

        callHandle();

        verify(ssq).scanAfterDelay_(any(Set.class), anyBoolean());
        verifyNoMoreInteractions(ssq);
    }


    private void callHandle()
    {
        mcn.handle_(ev, Prio.LO);
    }
}
