package com.aerofs.daemon.rest;

import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.mock.logical.MockDS;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.testlib.AbstractTest;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class for tests exercising the public REST API
 */
public class AbstractRestTest extends AbstractTest
{
    protected @Mock DirectoryService ds;
    protected @Mock ACLChecker acl;
    protected @Mock SIDMap sm;
    protected @Mock IStores ss;
    private @Mock CfgLocalUser localUser;
    protected @Mock NativeVersionControl nvc;

    protected MockDS mds;
    protected UserID user = UserID.fromInternal("foo@bar.baz");
    protected SID rootSID = SID.rootSID(user);

    private RestService service;

    @Before
    public void setUp() throws Exception
    {
        mds = new MockDS(rootSID, ds, sm, sm);

        when(localUser.get()).thenReturn(user);

        final IIMCExecutor imce = mock(IIMCExecutor.class);

        // inject mock objects into service
        Injector inj = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure()
            {
                bind(CfgLocalUser.class).toInstance(localUser);
                bind(IStores.class).toInstance(ss);
                bind(NativeVersionControl.class).toInstance(nvc);
                bind(DirectoryService.class).toInstance(ds);
                bind(ACLChecker.class).toInstance(acl);
                bind(IMapSID2SIndex.class).toInstance(sm);
                bind(IMapSIndex2SID.class).toInstance(sm);
                bind(CoreIMCExecutor.class).toInstance(new CoreIMCExecutor(imce));
            }
        });

        // wire event handlers (no queue, events are immediately executed)
        ICoreEventHandlerRegistrar reg = inj.getInstance(RestCoreEventHandlerRegistar.class);
        final CoreEventDispatcher disp = new CoreEventDispatcher(Collections.singleton(reg));
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                Object[] args = invocation.getArguments();
                disp.dispatch_((IEvent)args[0], (Prio)args[1]);
                return null;
            }
        }).when(imce).execute_(any(IEvent.class), any(Prio.class));

        // start REST service (listens on localhost:8080)
        service = new RestService(inj);
        service.start();
    }

    @After
    public void tearDown() throws Exception
    {
        service.stop();
    }

}
