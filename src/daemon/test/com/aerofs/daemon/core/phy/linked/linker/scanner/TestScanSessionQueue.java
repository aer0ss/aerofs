package com.aerofs.daemon.core.phy.linked.linker.scanner;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.lib.injectable.InjectableSystem;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.when;

public class TestScanSessionQueue extends AbstractTest
{
    @Mock CoreScheduler sched;
    @Mock ScanSession.Factory factSS;
    @Mock InjectableSystem sys;

    @InjectMocks ScanSessionQueue ssq;

    long START = 1111;

    @Before
    public void setup() throws Exception
    {
        when(sys.currentTimeMillis()).thenReturn(START);
    }

    @Test
    public void shouldScheduleFirstRequest()
    {
        l.warn("TODO implement the test");
    }

    @Test
    public void shouldReplaceExistingRequest()
    {
        l.warn("TODO implement the test");
    }
}
