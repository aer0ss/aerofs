package com.aerofs.daemon.core.lib;

import com.aerofs.daemon.core.ex.ExNoAvailDevice;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.ids.DID;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.mockito.stubbing.OngoingStubbing;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Helper class with common static methods needed setup/run (SA|Daemon)AsynDownloadTests
public class AsyncDownloadTestHelper
{
    public static Endpoint endpoint(final DID did)
    {
        return argThat(new BaseMatcher<Endpoint>()
        {
            @Override
            public boolean matches(Object o)
            {
                return did.equals(((Endpoint)o).did());
            }

            @Override
            public void describeTo(Description description)
            {
                description.appendText("ep(" + did + ")");
            }
        });
    }

    public static DigestedMessage anyDM() { return any(DigestedMessage.class); }

    public static DigestedMessage mockReply(DID replier, ITransport tp) throws Exception
    {
        DigestedMessage msg = mock(DigestedMessage.class);
        when(msg.did()).thenReturn(replier);
        when(msg.tp()).thenReturn(tp);
        when(msg.ep()).thenReturn(new Endpoint(tp, replier));
        return msg;
    }

    public static void mockDeviceSelection(To to, DID... dids) throws Exception
    {
        OngoingStubbing<DID> stubTo =
                when(to.pick_());
        for (DID d : dids) stubTo = stubTo.thenReturn(d);
        stubTo.thenThrow(new ExNoAvailDevice());
    }

}
