/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.online_status;

import com.aerofs.daemon.lib.CoreExecutor;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.aerofs.ritual_notification.RitualNotifier;
import com.aerofs.ssmp.SSMPClient.ConnectionListener;
import com.aerofs.ssmp.SSMPConnection;
import com.google.common.collect.ImmutableSet;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.NetworkInterface;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * N.B. the decision to send notification and the correctness of the snapshot both depends on the
 * current state, so it's necessary to test that the current state is correctly maintained.
 * Afterwards, we verify that the object behaves correctly based on the current state and external
 * events.
 */
public class TestOnlineStatusNotifier
{
    @Mock SSMPConnection _ssmp;
    @Mock LinkStateService _lss;
    @Mock RitualNotificationServer _ritualNotificationServer;
    @Mock CoreExecutor _coreExecutor;

    @Mock RitualNotifier _ritualNotifier;

    @InjectMocks OnlineStatusNotifier _notifier;

    // caching these since they will be used often;
    ImmutableSet<NetworkInterface> _empty = ImmutableSet.of();
    ImmutableSet<NetworkInterface> _nonEmpty;

    // these references are used to simulate callbacks
    ConnectionListener _ssmpListener;
    ILinkStateListener _linkStateListener;

    @Before
    public void setup() throws Exception
    {
        // le sigh...
        // NetworkInterface is final and powermock is borken...
        _nonEmpty = ImmutableSet.of(NetworkInterface.getNetworkInterfaces().nextElement());

        MockitoAnnotations.initMocks(this);

        when(_ritualNotificationServer.getRitualNotifier()).thenReturn(_ritualNotifier);

        _notifier.init_();

        ArgumentCaptor<ConnectionListener> ssmpCaptor = ArgumentCaptor.forClass(ConnectionListener.class);
        verify(_ssmp).addConnectionListener(ssmpCaptor.capture());
        _ssmpListener = ssmpCaptor.getValue();

        ArgumentCaptor<ILinkStateListener> linkStateCaptor =
                ArgumentCaptor.forClass(ILinkStateListener.class);
        verify(_lss).addListener(linkStateCaptor.capture(), any(Executor.class));
        _linkStateListener = linkStateCaptor.getValue();
    }

    @Test
    public void shouldBeOfflineInitially()
    {
        assertFalse(_notifier._isOnline);
        assertFalse(_notifier._isSSMPConnected);
        assertFalse(_notifier._isLinkStateConnected);
    }

    @Test
    public void shouldMaintainSSMPrAndLinkState()
    {
        boolean[] initialStates = { false, false, true, true };
        boolean[] externalStates = { false, true, false, true };
        boolean[] expectedStates = { false, true, false, true };

        for (int i = 0; i < initialStates.length; i++) {
            _notifier._isSSMPConnected = initialStates[i];
            triggerSSMPCallback(externalStates[i]);
            assertEquals(expectedStates[i], _notifier._isSSMPConnected);

            _notifier._isLinkStateConnected = initialStates[i];
            triggerLinkStateCallback(externalStates[i]);
            assertEquals(expectedStates[i], _notifier._isLinkStateConnected);
        }
    }

    @Test
    public void shouldMaintainOnlineState()
    {
        boolean[] ssmpTriggers = { false, false, true, true };
        boolean[] linkStateTriggers = { false, true, false, true };
        boolean[] expectedStates = { false, false, false, true };

        for (int i = 0; i < ssmpTriggers.length; i++) {
            triggerSSMPCallback(ssmpTriggers[i]);
            triggerLinkStateCallback(linkStateTriggers[i]);
            assertEquals(expectedStates[i], _notifier._isOnline);
        }
    }

    @Test
    public void shouldSendNotificationOnSend()
    {
        for (boolean isOnline : new boolean[] { false, true }) {
            _notifier._isOnline = isOnline;
            _notifier.sendOnlineStatusNotification();
            verify(_ritualNotifier).sendNotification(argThat(hasOnlineStatus(isOnline)));
        }
    }

    // this test is designed to cover all state transitions and ensure the notifier correctly send
    // and not send notifications on each transition.
    @Test
    public void shouldNotifyCorrectlyOnStateTransitions()
    {
        triggerSSMPCallback(true);
        verifyZeroInteractions(_ritualNotifier);

        triggerLinkStateCallback(true);
        verify(_ritualNotifier).sendNotification(argThat(hasOnlineStatus(true)));
        verifyNoMoreInteractions(_ritualNotifier);
        // the reset is necessary so that we can correctly account for each notification on
        // each state transition
        reset(_ritualNotifier);

        triggerSSMPCallback(false);
        verify(_ritualNotifier).sendNotification(argThat(hasOnlineStatus(false)));
        verifyNoMoreInteractions(_ritualNotifier);
        reset(_ritualNotifier);

        triggerLinkStateCallback(false);
        verifyZeroInteractions(_ritualNotifier);

        triggerLinkStateCallback(true);
        verifyZeroInteractions(_ritualNotifier);

        triggerSSMPCallback(true);
        verify(_ritualNotifier).sendNotification(argThat(hasOnlineStatus(true)));
        verifyNoMoreInteractions(_ritualNotifier);
        reset(_ritualNotifier);

        triggerLinkStateCallback(false);
        verify(_ritualNotifier).sendNotification(argThat(hasOnlineStatus(false)));
        verifyNoMoreInteractions(_ritualNotifier);
        reset(_ritualNotifier);

        triggerSSMPCallback(false);
        verifyZeroInteractions(_ritualNotifier);
    }

    // N.B. when we trigger callback like this, we are bypassing the core executor and are
    // effectively using the same thread executor.
    private void triggerSSMPCallback(boolean isOnline)
    {
        if (isOnline) {
            _ssmpListener.connected();
        } else {
            _ssmpListener.disconnected();
        }
    }

    // N.B. when we trigger callback like this, we are bypassing the core executor and are
    // effectively using the same thread executor.
    private void triggerLinkStateCallback(boolean isOnline)
    {
        _linkStateListener.onLinkStateChanged(_empty, isOnline ? _nonEmpty : _empty, _empty, _empty);
    }

    // method awkwardly named to go along with Mockito speak
    private Matcher<PBNotification> hasOnlineStatus(final boolean isOnline)
    {
        return new ArgumentMatcher<PBNotification>()
        {
            @Override
            public boolean matches(Object o)
            {
                if (o instanceof PBNotification) {
                    PBNotification notf = (PBNotification) o;
                    return notf.getType() == Type.ONLINE_STATUS_CHANGED
                            && notf.hasOnlineStatus()
                            && notf.getOnlineStatus() == isOnline;
                } else {
                    return false;
                }
            }
        };
    }
}
