package com.aerofs.daemon.core.linker.notifier.osx;

import java.util.Set;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.linker.scanner.ScanSessionQueue;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.injectable.InjectableJNotify;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
import net.contentobjects.jnotify.JNotifyException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TestOSXNotifier extends AbstractTest
{
    @Mock CoreQueue cq;
    @Mock ScanSessionQueue ssq;
    @Mock InjectableJNotify jn;
    @Mock CfgAbsRootAnchor cfgAbsRootAnchor;
    @InjectMocks OSXNotifier notifier;

    @Captor ArgumentCaptor<Set<String>> capPaths;

    int id = 123;

    String root = "foo";
    String curName = "barr";

    @Before
    public void setup() throws JNotifyException
    {
        when(jn.macosx_addWatch(any(String.class))).thenReturn(id);
        notifier.start_();

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                AbstractEBSelfHandling ev = (AbstractEBSelfHandling) invocation.getArguments()[0];
                ev.handle_();
                return null;
            }
        }).when(cq).enqueueBlocking(any(IEvent.class), any(Prio.class));
    }

    ////////
    // enforcement tests
    //
    // "ID matches" refer to the fact that the ID passed into batchStart/notifyChagne/batchEnd
    // must be the same as what has been returned from macosx_addWatch.

    @Test(expected = AssertionError.class)
    public void shouldAssertIDMatchesInBatchStart()
    {
        notifier.batchStart(0);
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertIDMatchesInBatchEnd()
    {
        notifier.batchStart(id);
        notifyAChange(id, false);
        notifier.batchEnd(0);
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertIDMatchesInNotifyChange()
    {
        notifier.batchStart(id);
        notifyAChange(0, false);
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertBatchStartBeforeBatchEnd()
    {
        notifier.batchStart(id);
        notifyAChange(id, false);
        notifier.batchEnd(id);
        notifier.batchEnd(id);
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertNonEmptyBatches()
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

        verify(ssq).scanImmediately_(capPaths.capture(), eq(false));
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

        verify(ssq).scanImmediately_(capPaths.capture(), eq(true));
        assertEquals(capPaths.getValue().size(), 3);
    }


    @Test
    public void shouldNotPruneBatch()
    {
        Set<String> paths = ImmutableSet.of("/abc/ade/efg", "/abc/cde", "/bcd/gte", "/bcd/abc");
        notifier.batchStart(id);
        for (String path : paths) {
            // root is only used in an assert in notifyChange
            notifier.notifyChange(id, "/", path, false);
        }
        notifier.batchEnd(id);
        verify(ssq).scanImmediately_(paths, false);
    }

    @Test
    public void shouldPruneChildPathsFromBatch()
    {
        Set<String> prunedPaths = ImmutableSet.of("/abc", "/bcd");
        Set<String> paths = ImmutableSet.of("/abc/ade/efg", "/abc", "/abc/cde", "/bcd/abc", "/bcd");
        notifier.batchStart(id);
        for (String path : paths) {
            // root is only used in an assert in notifyChange
            notifier.notifyChange(id, "/", path, false);
        }
        notifier.batchEnd(id);
        verify(ssq).scanImmediately_(prunedPaths, false);
    }

    private void notifyAChange(int id, boolean recurse)
    {
        String name = curName;
        curName += "z";
        notifier.notifyChange(id, root, name, recurse);
    }
}
