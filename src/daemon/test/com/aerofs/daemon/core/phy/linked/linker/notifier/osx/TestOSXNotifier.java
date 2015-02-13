package com.aerofs.daemon.core.phy.linked.linker.notifier.osx;

import java.util.Set;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.injectable.InjectableJNotify;
import com.aerofs.testlib.AbstractTest;
import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.macosx.JNotify_macosx;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TestOSXNotifier extends AbstractTest
{
    @Mock LinkerRoot lr;
    @Mock CoreQueue cq;
    @Mock InjectableJNotify jn;

    @InjectMocks OSXNotifier notifier;

    @Captor ArgumentCaptor<Set<String>> capPaths;

    // A watch ID that we set up
    int id = 123;
    // A watch ID that we don't set up which should trigger failure conditions
    int unknownWatchId = 0;

    final String root = "foo";
    String curName = "barr";
    final SID rootSID = SID.generate();

    @Before
    public void setup() throws JNotifyException
    {
        when(lr.sid()).thenReturn(rootSID);
        when(lr.absRootAnchor()).thenReturn(root);

        when(jn.macosx_addWatch(any(String.class))).thenReturn(id);

        notifier.start_();
        notifier.addRootWatch_(lr);

        doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                AbstractEBSelfHandling ev = (AbstractEBSelfHandling)invocation.getArguments()[0];
                ev.handle_();
                return null;
            }
        }).when(cq).enqueueBlocking(any(IEvent.class), any(Prio.class));
    }

    ////////
    // enforcement tests
    //
    // "ID matches" refer to the fact that the ID passed into batchStart/notifyChange/batchEnd
    // must be the same as what has been returned from macosx_addWatch.

    @Test
    public void shouldIgnoreBatchStartForUnknownID()
    {
        notifier.batchStart(unknownWatchId);
    }

    @Test
    public void shouldIgnoreBatchEndForUnknownID()
    {
        notifier.batchStart(id);
        notifyAChange(id, false);
        notifier.batchEnd(unknownWatchId);
        verify(cq, never()).enqueueBlocking(any(IEvent.class), any(Prio.class));
    }

    @Test
    public void shouldIgnoreNotifyChangeForUnknownID()
    {
        notifier.batchStart(id);
        notifyAChange(unknownWatchId, false);
    }

    @Test(expected = NullPointerException.class)
    public void shouldVerifyBatchStartBeforeBatchEnd()
    {
        notifier.batchStart(id);
        notifyAChange(id, false);
        notifier.batchEnd(id);
        notifier.batchEnd(id);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldVerifyNonEmptyBatches()
    {
        notifier.batchStart(id);
        notifier.batchEnd(id);
    }

    ////////
    // functional tests

    @Test
    public void shouldIncludeAllNotifiedChanges()
    {
        notifier.batchStart(id);
        notifyAChange(id, false);
        notifyAChange(id, false);
        notifyAChange(id, false);
        notifier.batchEnd(id);

        verify(lr).scanImmediately_(capPaths.capture(), eq(false));
        assertEquals(capPaths.getValue().size(), 3);
    }

    @Test
    public void shouldRecurseIfAtLeastOneChangeIsRecursvie()
    {
        notifier.batchStart(id);
        notifyAChange(id, false);
        notifyAChange(id, true);
        notifyAChange(id, false);
        notifier.batchEnd(id);

        verify(lr).scanImmediately_(capPaths.capture(), eq(true));
        assertEquals(capPaths.getValue().size(), 3);
    }

    private void notifyAChange(int id, boolean recurse)
    {
        String name = curName;
        curName += "z";
        notifier.notifyChange(id, name, recurse ? JNotify_macosx.MUST_SCAN_SUBDIRS : 0);
    }
}
