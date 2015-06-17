/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp.presence;

import com.aerofs.daemon.transport.lib.presence.IPresenceLocationReceiver;
import com.aerofs.daemon.transport.presence.IStoreInterestListener;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.testlib.LoggerSetup;
import com.aerofs.daemon.transport.lib.IMulticastListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Type;
import org.junit.Before;
import org.junit.Test;

import static com.aerofs.base.id.JabberID.did2user;
import static com.aerofs.base.id.JabberID.sid2muc;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public final class TestXMPPPresenceProcessor
{
    static
    {
        LoggerSetup.init();
    }

    private static final SID SID_0 = SID.generate();
    private static final SID SID_1 = SID.generate();

    private static final DID LOCAL_DID = DID.generate();

    private static final DID DID_0 = DID.generate();
    private static final String XMPP_SERVER_DOMAIN = "arrowfs.org";

    private final XMPPConnection xmppConnection = mock(XMPPConnection.class);
    private final IMulticastListener multicastListener = mock(IMulticastListener.class);
    private final IStoreInterestListener storeInterestListener = mock(IStoreInterestListener.class);
    private final IPresenceLocationReceiver presenceLocationReceiver = mock(IPresenceLocationReceiver.class);

    private XMPPPresenceProcessor presenceProcessor;

    @Before
    public void setup()
    {
        presenceProcessor = new XMPPPresenceProcessor(LOCAL_DID, XMPP_SERVER_DOMAIN,
                multicastListener, storeInterestListener, presenceLocationReceiver);
    }

    private String getFrom(SID sid, DID remotedid)
    {
        return String.format("%s/%s", sid2muc(sid, XMPP_SERVER_DOMAIN), did2user(remotedid));
    }

    private Presence getPresence(DID remotedid, SID sid, boolean isAvailable)
    {
        Presence presence = new Presence(isAvailable ? Type.available : Type.unavailable);
        presence.setFrom(getFrom(sid, remotedid));
        return presence;
    }

    private void beInTheMoment(DID remotedid, SID sid)
            throws ExInvalidID
    {
        boolean processed = presenceProcessor.processPresenceForUnitTests(getPresence(remotedid, sid, true));
        assertThat(processed, equalTo(true));
    }

    private void leaveTheMoment(DID remotedid, SID sid)
            throws ExInvalidID
    {
        boolean processed = presenceProcessor.processPresenceForUnitTests(getPresence(remotedid, sid, false));
        assertThat(processed, equalTo(true));
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

    @Test
    public void shouldIgnorePresenceNotificationsFromOtherTransports()
            throws Exception
    {
        Presence presence = getPresence(DID_0, SID_0, true);
        boolean processed = presenceProcessor.processPresenceForUnitTests(presence);
        assertThat(processed, equalTo(false));
    }

    @Test
    public void shouldIgnorePresenceNotificationsFromSelf()
            throws Exception
    {
        Presence presence = getPresence(LOCAL_DID, SID_0, true);
        boolean processed = presenceProcessor.processPresenceForUnitTests(presence);
        assertThat(processed, equalTo(false));
    }

    @Test
    public void shouldNotifyMulticastListenerOnlyOnceButSendMultipleNotificationsToTheCoreEveryTimeADeviceJoinsAMUCRoom()
            throws Exception
    {
        beInTheMoment(DID_0, SID_0);
        beInTheMoment(DID_0, SID_1);

        verify(multicastListener, times(1)).onDeviceReachable(DID_0);
        verify(storeInterestListener).onDeviceJoin(DID_0, SID_0);
        verify(storeInterestListener).onDeviceJoin(DID_0, SID_1);
    }

    @Test
    public void shouldSendIncrementalNotificationsIfUnicastAlreadyOnline() throws Exception
    {
        beInTheMoment(DID_0, SID_0);
        verify(storeInterestListener).onDeviceJoin(DID_0, SID_0);

        beInTheMoment(DID_0, SID_1);
        verify(storeInterestListener).onDeviceJoin(DID_0, SID_1);

        leaveTheMoment(DID_0, SID_0);
        verify(storeInterestListener).onDeviceLeave(DID_0, SID_0);
    }

    /**
     * The client should send a non-empty metadata inside the vCard
     * @throws Exception
     */
    @Test
    public void shouldSendMetadata() throws Exception
    {
        beInTheMoment(DID_0, SID_0);
        // How to test that the vCard has been attached / is accessible?
    }
}
