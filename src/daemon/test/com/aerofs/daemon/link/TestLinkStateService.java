/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.link;

import com.aerofs.base.C;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Queues;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.NetworkInterface;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.theInstance;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

// What's with the long test names?
// Allen George is going for the "longest test names in the AeroFS codebase" record.
// Oh.
// ...
// Well, that seems a bit strange.
// You know how he is.
// Do you think this record will last for ever?
// You mean like forever ever? Forever ever? Forever ever? Forever ever?
// Sure.
// I hope so.
// Me too.
@SuppressWarnings("unchecked")
public class TestLinkStateService
{
    @Rule public final Timeout _timeoutRule = new Timeout((int) (10 * C.SEC));

    private final Semaphore _notificationSemaphore = new Semaphore(0);
    private final ILinkStateListener _listener = mock(ILinkStateListener.class);
    private final Executor _notificationExecutor = newSingleThreadExecutor();
    private final ConcurrentLinkedQueue<LinkStateNotificationParameters> calls = Queues.newConcurrentLinkedQueue();

    private LinkStateService _linkStateService;
    private volatile Thread _executorThread = null;

    private class LinkStateNotificationParameters
    {
        private final ImmutableSet<NetworkInterface> _previous;
        private final ImmutableSet<NetworkInterface> _current;
        private final ImmutableSet<NetworkInterface> _added;
        private final ImmutableSet<NetworkInterface> _removed;
        private final Thread _notificationThread;

        private LinkStateNotificationParameters(
                ImmutableSet<NetworkInterface> previous,
                ImmutableSet<NetworkInterface> current,
                ImmutableSet<NetworkInterface> added,
                ImmutableSet<NetworkInterface> removed,
                Thread notificationThread)
        {
            this._previous = previous;
            this._current = current;
            this._added = added;
            this._removed = removed;
            this._notificationThread = notificationThread;
        }
    }

    @Before
    public void setup()
            throws InterruptedException
    {
        _notificationExecutor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                _executorThread = Thread.currentThread();
                _notificationSemaphore.release();
            }
        });

        // wait until we've got the executor thread id
        _notificationSemaphore.acquire();

        Preconditions.checkState(_executorThread != null);

        _linkStateService = spy(new LinkStateService());
        _linkStateService.addListener(_listener, _notificationExecutor); // want to be notified on single-thread-executor
    }

    @Test
    public void shouldNotifyListenerOnCorrectThreadWithEmptyNetworkInterfaceSetWhenLinksMarkedDown()
            throws InterruptedException
    {
        // [sigh] I have to do this because we have a check on
        // LinkStateService:178 that will not send out a notification
        // if there has been no link state change
        // to work around this I will pull the links up (which will force
        // an interface check), and then pull it down, to see if something happened

        doAnswer(new Answer<Object>()
        {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                calls.add(new LinkStateNotificationParameters(
                        getArgument(invocation, 0),
                        getArgument(invocation, 1),
                        getArgument(invocation, 2),
                        getArgument(invocation, 3),
                        Thread.currentThread()));

                _notificationSemaphore.release();

                return null;
            }
        }).when(_listener).onLinkStateChanged(
                org.mockito.Matchers.<ImmutableSet<NetworkInterface>>any(),
                org.mockito.Matchers.<ImmutableSet<NetworkInterface>>any(),
                org.mockito.Matchers.<ImmutableSet<NetworkInterface>>any(),
                org.mockito.Matchers.<ImmutableSet<NetworkInterface>>any());

        // switch links up to setup state
        _linkStateService.markLinksUp();

        // take links down (this is the method under test)
        _linkStateService.markLinksDown();

        // wait until both calls have completed
        _notificationSemaphore.acquire();
        _notificationSemaphore.acquire();

        // verify the output of LinkStateService

        LinkStateNotificationParameters call;

        // only had two calls to the listener
        assertThat(calls.size(), equalTo(2));

        // call 1 (setup): when links marked up
        call = calls.poll();
        assertThat(call._current.size(), greaterThan(0));
        assertThat(call._previous.size(), equalTo(0));
        assertThat(call._added.size(), equalTo(call._current.size()));
        assertThat(call._removed.size(), equalTo(0));
        assertThat(call._notificationThread, theInstance(_executorThread));

        // call 2 (actual call under test): when links marked down
        call = calls.poll();
        assertThat(call._current.size(), equalTo(0));
        assertThat(call._previous.size(), greaterThan(0));
        assertThat(call._added.size(), equalTo(0));
        assertThat(call._removed.size(), equalTo(call._previous.size()));
        assertThat(call._notificationThread, theInstance(_executorThread));
    }

    @Test
    public void shouldNotifyListenerOnCorrectThreadWithNonEmptyNetworkInterfaceSetWhenLinksMarkedUp()
            throws InterruptedException {
            // [sigh] I have to do this because we have a check on
        // LinkStateService:178 that will not send out a notification
        // if there has been no link state change
        // to work around this I will pull the links up (which will force
        // an interface check), and then pull it down, to see if something happened

        doAnswer(new Answer<Object>()
        {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable
            {
                calls.add(new LinkStateNotificationParameters(
                        getArgument(invocation, 0),
                        getArgument(invocation, 1),
                        getArgument(invocation, 2),
                        getArgument(invocation, 3),
                        Thread.currentThread()));

                _notificationSemaphore.release();

                return null;
            }
        }).when(_listener).onLinkStateChanged(
                org.mockito.Matchers.<ImmutableSet<NetworkInterface>>any(),
                org.mockito.Matchers.<ImmutableSet<NetworkInterface>>any(),
                org.mockito.Matchers.<ImmutableSet<NetworkInterface>>any(),
                org.mockito.Matchers.<ImmutableSet<NetworkInterface>>any());

        // switch links up/down to setup state
        _linkStateService.markLinksUp();
        _linkStateService.markLinksDown();

        // this is the call under test
        _linkStateService.markLinksUp();

        // wait until all calls have completed
        _notificationSemaphore.acquire();
        _notificationSemaphore.acquire();
        _notificationSemaphore.acquire();

        // verify the output of LinkStateService

        LinkStateNotificationParameters call;

        // had three calls to the listener
        assertThat(calls.size(), equalTo(3));

        // call 1 (setup): when links marked up
        call = calls.poll();
        assertThat(call._current.size(), greaterThan(0));
        assertThat(call._previous.size(), equalTo(0));
        assertThat(call._added.size(), equalTo(call._current.size()));
        assertThat(call._removed.size(), equalTo(0));
        assertThat(call._notificationThread, theInstance(_executorThread));

        // call 2 (setup): when links marked down
        call = calls.poll();
        assertThat(call._current.size(), equalTo(0));
        assertThat(call._previous.size(), greaterThan(0));
        assertThat(call._added.size(), equalTo(0));
        assertThat(call._removed.size(), equalTo(call._previous.size()));
        assertThat(call._notificationThread, theInstance(_executorThread));

        // call 3 (actual call under test)
        call = calls.poll();
        assertThat(call._current.size(), greaterThan(0));
        assertThat(call._previous.size(), equalTo(0));
        assertThat(call._added.size(), equalTo(call._current.size()));
        assertThat(call._removed.size(), equalTo(0));
        assertThat(call._notificationThread, theInstance(_executorThread));
    }

    private static ImmutableSet<NetworkInterface> getArgument(InvocationOnMock invocation, int index)
    {
        return (ImmutableSet<NetworkInterface>)invocation.getArguments()[index];
    }
}
