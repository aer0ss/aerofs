/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.first_launch.OIDGenerator;
import com.aerofs.daemon.core.phy.linked.FileSystemProber;
import com.aerofs.daemon.core.phy.linked.linker.event.EIMightCreateNotification;
import com.aerofs.daemon.core.phy.linked.linker.event.EIMightCreateNotification.RescanSubtree;
import com.aerofs.daemon.core.phy.linked.linker.scanner.ScanSessionQueue;
import com.aerofs.lib.Util;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TestHdMightCreateNotification extends AbstractTest
{
    @Mock Trans t;
    @Mock TransManager tm;
    @Mock MightCreate mc;
    @Mock MightDelete md;
    @Mock IDeletionBuffer delBuffer;
    @Mock ScanSessionQueue ssq;
    @Mock ScanSessionQueue.Factory factSSQ;
    @Mock LinkerRootMap lrm;
    @Mock FileSystemProber prober;

    @InjectMocks LinkerRoot.Factory factLR;
    @InjectMocks HdMightCreateNotification mcn;

    static final String absRootAnchor = "root";
    static final SID rootSID = SID.generate();

    @Before
    public void setup()
    {
        when(tm.begin_()).thenReturn(t);
        when(factSSQ.create_(any(LinkerRoot.class))).thenReturn(ssq);
        when(ssq.absRootanchor()).thenReturn(absRootAnchor);
        when(ssq.sid()).thenReturn(rootSID);
    }

    @Test
    public void shouldIgnoreExNotFoundFromMightCreate()
            throws Exception
    {
        when(mc.mightCreate_(any(PathCombo.class), any(IDeletionBuffer.class),
                any(OIDGenerator.class), any(Trans.class)))
                .thenThrow(new ExNotFound());

        callHandle();

        verify(mc).mightCreate_(any(PathCombo.class), eq(delBuffer), any(OIDGenerator.class), eq(t));
        verifyZeroInteractions(ssq);
    }

    @Test
    public void shouldTriggerFullScanOnOtherExceptionsFromMightCreate()
            throws Exception
    {
        when(mc.mightCreate_(any(PathCombo.class), any(IDeletionBuffer.class),
                any(OIDGenerator.class), any(Trans.class)))
                .thenThrow(new ExNoPerm());

        callHandle();

        verify(mc).mightCreate_(any(PathCombo.class), eq(delBuffer), any(OIDGenerator.class), eq(t));
        verify(ssq).scanAfterDelay_(anySetOf(String.class), anyBoolean());
        verifyNoMoreInteractions(ssq);
    }


    private void callHandle() throws Exception
    {
        mcn.handle_(new EIMightCreateNotification(factLR.create_(rootSID, absRootAnchor),
                Util.join(absRootAnchor, "test"), RescanSubtree.DEFAULT));
    }
}
