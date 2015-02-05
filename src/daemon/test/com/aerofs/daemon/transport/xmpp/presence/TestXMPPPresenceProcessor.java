/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp.presence;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID.ExInvalidID;
import com.aerofs.daemon.event.net.EIStoreAvailability;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.testlib.LoggerSetup;
import com.aerofs.daemon.transport.lib.IMulticastListener;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Type;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Collection;
import java.util.Map;

import static com.aerofs.base.id.JabberID.did2user;
import static com.aerofs.base.id.JabberID.sid2muc;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class TestXMPPPresenceProcessor
{
    static
    {
        LoggerSetup.init();
    }

    private static final String TRANSPORT_ID = "z";

    private static final SID SID_0 = SID.generate();
    private static final SID SID_1 = SID.generate();
    private static final SID SID_2 = SID.generate();

    private static final DID LOCAL_DID = DID.generate();

    private static final DID DID_0 = DID.generate();
    private static final DID DID_1 = DID.generate();
    private static final String OTHER_TRANSPORT_ID = "j";
    private static final String XMPP_SERVER_DOMAIN = "arrowfs.org";

    private final ITransport transport = mock(ITransport.class);
    private final XMPPConnection xmppConnection = mock(XMPPConnection.class);
    private final BlockingPrioQueue<IEvent> outgoingEventSink = new BlockingPrioQueue<IEvent>(10);
    private final IMulticastListener multicastListener = mock(IMulticastListener.class);

    private XMPPPresenceProcessor presenceProcessor;

    @Before
    public void setup()
    {
        when(transport.id()).thenReturn(TRANSPORT_ID);
        presenceProcessor = new XMPPPresenceProcessor(LOCAL_DID, XMPP_SERVER_DOMAIN, transport, outgoingEventSink, multicastListener);
    }

    private String getFrom(SID sid, String transportId, DID remotedid)
    {
        return String.format("%s/%s-%s", sid2muc(sid, XMPP_SERVER_DOMAIN), did2user(remotedid), transportId);
    }

    private Presence getPresence(DID remotedid, SID sid, String transportId, boolean isAvailable)
    {
        Presence presence = new Presence(isAvailable ? Type.available : Type.unavailable);
        presence.setFrom(getFrom(sid, transportId, remotedid));
        return presence;
    }

    private void beInTheMoment(DID remotedid, SID sid)
            throws ExInvalidID
    {
        boolean processed = presenceProcessor.processPresenceForUnitTests(getPresence(remotedid, sid, TRANSPORT_ID, true));
        assertThat(processed, equalTo(true));
    }

    private void leaveTheMoment(DID remotedid, SID sid)
            throws ExInvalidID
    {
        boolean processed = presenceProcessor.processPresenceForUnitTests(getPresence(remotedid, sid, TRANSPORT_ID, false));
        assertThat(processed, equalTo(true));
    }

    // IMPORTANT: this will _not_ handle a mix of online and offline presence
    // it's only meant to deal with queued presence of one type
    private Multimap<DID, SID> squashAndGetAllPendingCoreNotifications(boolean expectedOnline)
    {
        Multimap<DID, SID> didToSids = TreeMultimap.create();

        EIStoreAvailability presence;
        while ((presence = (EIStoreAvailability) outgoingEventSink.tryDequeue(new OutArg<Prio>(Prio.LO))) != null) {
            assertThat(presence._online, equalTo(expectedOnline));

            for (Map.Entry<DID, Collection<SID>> entry : presence._did2sids.entrySet()) {
                DID key = entry.getKey();
                Collection<SID> sids = entry.getValue();

                for (SID sid : sids) {
                    didToSids.put(key, sid);
                }
            }
        }

        return didToSids;
    }

    private void drainOutgoingEventQueue()
    {
        // noinspection StatementWithEmptyBody
        while (outgoingEventSink.tryDequeue(new OutArg<Prio>(null)) != null) {
            // this space intentially left blank
        }
    }

    @Test
    public void shouldNotifyMulticastListenerThatServiceIsReadyAndAddAPacketListenerIfXmppServerConnected()
            throws Exception
    {
        presenceProcessor.xmppServerConnected(xmppConnection);
        verify(xmppConnection).addPacketListener(any(PacketListener.class), any(PacketFilter.class));
        verify(multicastListener).onMulticastReady();
    }

    @Test
    public void shouldNotifyMulticastListenerThatServiceIsUnavailableIfXmppServerDisconnected()
    {
        presenceProcessor.xmppServerDisconnected();
        verify(multicastListener).onMulticastUnavailable();
    }

    // done because whenever we get a presence notification via the XMPP connection we send it to the core immediately
    // the device presence notification only lets us know of presence edges, and does not contain SID information, which the
    // core wants
    @Test
    public void shouldNotNotifyCoreIfDevicePresenceListenerIsTriggeredAndADeviceBecomesPotentiallyAvailable()
    {
        presenceProcessor.onDevicePresenceChanged(DID_0, true);
        assertThat(outgoingEventSink.tryDequeue(new OutArg<Prio>(null)), nullValue());
    }

    @Test
    public void shouldNotifyCoreIfADeviceHasSIDsAndDevicePresenceListenerNotifiesUsThatTheDeviceHasBecomeUnavailable()
            throws Exception
    {
        beInTheMoment(DID_0, SID_0);
        beInTheMoment(DID_0, SID_0);
        beInTheMoment(DID_0, SID_1);
        beInTheMoment(DID_0, SID_2);
        beInTheMoment(DID_1, SID_2);

        // drain all the notifications related to devices coming online
        drainOutgoingEventQueue();

        // now, go offline
        presenceProcessor.onDevicePresenceChanged(DID_0, false);

        Multimap<DID, SID> notifications = squashAndGetAllPendingCoreNotifications(false);
        assertThat(notifications.containsKey(DID_0), equalTo(true));
        assertThat(notifications.containsKey(DID_1), equalTo(false));
        assertThat(notifications.get(DID_0), containsInAnyOrder(SID_0, SID_1, SID_2));
    }

    @Test
    public void shouldNotNotifyCoreIfADeviceHasNoSIDsAndDevicePresenceListenerNotifiesUsThatTheDeviceHasBecomeUnavailable()
    {
        presenceProcessor.onDevicePresenceChanged(DID_0, false);
        assertThat(outgoingEventSink.tryDequeue(new OutArg<Prio>(null)), nullValue());
    }

    @Test
    public void shouldIgnorePresenceNotificationsFromOtherTransports()
            throws Exception
    {
        Presence presence = getPresence(DID_0, SID_0, OTHER_TRANSPORT_ID, true);
        boolean processed = presenceProcessor.processPresenceForUnitTests(presence);
        assertThat(processed, equalTo(false));
    }

    @Test
    public void shouldIgnorePresenceNotificationsFromSelf()
            throws Exception
    {
        Presence presence = getPresence(LOCAL_DID, SID_0, TRANSPORT_ID, true);
        boolean processed = presenceProcessor.processPresenceForUnitTests(presence);
        assertThat(processed, equalTo(false));
    }

    @Test
    public void shouldNotifyMulticastListenerOnlyOnceButSendMultipleNotificationsToTheCoreEveryTimeADeviceJoinsAMUCRoom()
            throws Exception
    {
        // mark the device online so presence notifications are sent:
        presenceProcessor.onDevicePresenceChanged(DID_0, true);

        beInTheMoment(DID_0, SID_0);
        beInTheMoment(DID_0, SID_1);

        verify(multicastListener, times(1)).onDeviceReachable(DID_0);

        Multimap<DID, SID> squashedPresence = squashAndGetAllPendingCoreNotifications(true);
        assertThat(squashedPresence.containsKey(DID_0), equalTo(true));
        assertThat(squashedPresence.get(DID_0), containsInAnyOrder(SID_0, SID_1));
    }

    @Test
    public void shouldSendNotificationsAfterUnicastComesOnline() throws Exception
    {
        beInTheMoment(DID_0, SID_0);
        beInTheMoment(DID_0, SID_1);

        verify(multicastListener, times(1)).onDeviceReachable(DID_0);
        Multimap<DID, SID> squashedPresence = squashAndGetAllPendingCoreNotifications(true);
        assertThat("unexpected notification", squashedPresence.isEmpty());

        presenceProcessor.onDevicePresenceChanged(DID_0, true);

        squashedPresence = squashAndGetAllPendingCoreNotifications(true);
        assertThat(squashedPresence.containsKey(DID_0), equalTo(true));
        assertThat(squashedPresence.get(DID_0), containsInAnyOrder(SID_0, SID_1));
    }

    @Test
    public void shouldSendIncrementalNotificationsIfUnicastAlreadyOnline() throws Exception
    {
        presenceProcessor.onDevicePresenceChanged(DID_0, true);

        beInTheMoment(DID_0, SID_0);
        assertThat(squashAndGetAllPendingCoreNotifications(true).get(DID_0), containsInAnyOrder(SID_0));

        beInTheMoment(DID_0, SID_1);
        assertThat(squashAndGetAllPendingCoreNotifications(true).get(DID_0), containsInAnyOrder(SID_1));

        leaveTheMoment(DID_0, SID_0);
        assertThat(squashAndGetAllPendingCoreNotifications(false).get(DID_0), containsInAnyOrder(SID_0));

        presenceProcessor.onDevicePresenceChanged(DID_0, false);
        assertThat(squashAndGetAllPendingCoreNotifications(false).get(DID_0), containsInAnyOrder(SID_1));
    }

    @Test
    public void shouldSendOfflineNotificationsForAllStores() throws Exception
    {
        presenceProcessor.onDevicePresenceChanged(DID_0, true);

        beInTheMoment(DID_0, SID_0);
        beInTheMoment(DID_0, SID_1);

        assertThat(squashAndGetAllPendingCoreNotifications(true).get(DID_0), containsInAnyOrder(SID_0, SID_1));

        presenceProcessor.onDevicePresenceChanged(DID_0, false);
        assertThat(squashAndGetAllPendingCoreNotifications(false).get(DID_0), containsInAnyOrder(SID_0, SID_1));
    }

    @Test
    public void shouldNotSendOfflineNotificationsForOfflineDevices() throws Exception
    {
        presenceProcessor.onDevicePresenceChanged(DID_0, true);
        beInTheMoment(DID_0, SID_0);
        beInTheMoment(DID_0, SID_1);
        assertThat(squashAndGetAllPendingCoreNotifications(true).get(DID_0), containsInAnyOrder(SID_0, SID_1));

        presenceProcessor.onDevicePresenceChanged(DID_0, false);
        assertThat(squashAndGetAllPendingCoreNotifications(false).get(DID_0), containsInAnyOrder(SID_0, SID_1));

        leaveTheMoment(DID_0, SID_0);
        leaveTheMoment(DID_0, SID_1);
        assertThat("no offline events", squashAndGetAllPendingCoreNotifications(false).isEmpty());
    }

    @Test
    public void shouldNotifyMulticastListenerOnlyOnceButSendMultipleNotificationsToTheCoreEveryTimeADeviceLeavesAMUCRoom()
            throws Exception
    {
        // mark the device online so presence notifications are sent:
        presenceProcessor.onDevicePresenceChanged(DID_0, true);

        beInTheMoment(DID_0, SID_0);
        beInTheMoment(DID_0, SID_1);

        // drain all notifications related to the device coming online
        drainOutgoingEventQueue();

        leaveTheMoment(DID_0, SID_0);
        leaveTheMoment(DID_0, SID_1);

        // verify that we were first told once, that it was online, and then once, that it was offline
        InOrder notificationOrder = inOrder(multicastListener);
        notificationOrder.verify(multicastListener).onDeviceReachable(DID_0);
        notificationOrder.verify(multicastListener).onDeviceUnreachable(DID_0);
        notificationOrder.verifyNoMoreInteractions();

        Multimap<DID, SID> squashedPresence = squashAndGetAllPendingCoreNotifications(false);
        assertThat(squashedPresence.containsKey(DID_0), equalTo(true));
        assertThat(squashedPresence.get(DID_0), containsInAnyOrder(SID_0, SID_1));
    }

    @Test
    public void shouldSendNotificationsForRelevantStoresOnly() throws Exception
    {
        leaveTheMoment(DID_0, SID_0);
        beInTheMoment(DID_0, SID_0);
        beInTheMoment(DID_0, SID_1);

        // mark the device online so presence notifications are sent:
        presenceProcessor.onDevicePresenceChanged(DID_0, true);

        // drain all notifications related to the device coming online
        drainOutgoingEventQueue();

        leaveTheMoment(DID_0, SID_1);

        Multimap<DID, SID> squashedPresence = squashAndGetAllPendingCoreNotifications(false);
        assertThat(squashedPresence.size(), equalTo(1));
        assertThat(squashedPresence.get(DID_0), containsInAnyOrder(SID_1));
    }
}
