/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.net.TransportFactory.ExUnsupportedTransport;
import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ConfigurationPropertiesResource;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.LoggerResource;
import com.aerofs.daemon.transport.jingle.Jingle;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.zephyr.Zephyr;
import com.aerofs.lib.cfg.CfgAbsRTRoot;
import com.aerofs.lib.cfg.CfgEnabledTransports;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgLolol;
import com.aerofs.lib.cfg.CfgScrypted;
import com.aerofs.rocklog.RockLog;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestCoreTransports
{
    private final CfgAbsRTRoot _absRTRoot = mock(CfgAbsRTRoot.class);
    private final CfgLocalUser _localUser = mock(CfgLocalUser.class);
    private final CfgLocalDID _localDID = mock(CfgLocalDID.class);
    private final CfgScrypted _scrypted = mock(CfgScrypted.class);
    private final CfgLolol _cfgLolol = mock(CfgLolol.class);
    private final CoreQueue _coreQueue = mock(CoreQueue.class);
    private final TC _tc = mock(TC.class);
    private final MaxcastFilterReceiver _maxcastFilterReceiver = mock(MaxcastFilterReceiver.class);
    private final LinkStateService _linkStateService = mock(LinkStateService.class);
    private final RockLog _rockLog = mock(RockLog.class);
    private final ClientSSLEngineFactory _clientSslEngineFactory = mock(ClientSSLEngineFactory.class);
    private final ServerSSLEngineFactory _serverSSLEngineFactory = mock(ServerSSLEngineFactory.class);
    private final ClientSocketChannelFactory _clientSocketChannelFactory = mock(ClientSocketChannelFactory.class);
    private final ServerSocketChannelFactory _serverSocketChannelFactory = mock(ServerSocketChannelFactory.class);

    @ClassRule
    public static LoggerResource _loggerResource = new LoggerResource(TestCoreTransports.class);

    @ClassRule
    public static ConfigurationPropertiesResource _configurationPropertiesResource = new ConfigurationPropertiesResource();

    @Rule
    public TemporaryFolder _temporaryFolder = new TemporaryFolder();

    @Before
    public void setup()
    {
        when(_localDID.get()).thenReturn(DID.generate());
        when(_scrypted.get()).thenReturn(new byte[]{0x00, 0x01, 0x02, 0x03});
        when(_absRTRoot.get()).thenReturn(_temporaryFolder.getRoot().getAbsolutePath());
    }

    @Test
    public void shouldEnableMulticastForZephyrWhenJingleIsUnavailable()
            throws ExUnsupportedTransport
    {
        CfgEnabledTransports enabledTransports = mock(CfgEnabledTransports.class);

        // disable every transport other than zephyr
        // even in this configuration multicast should be enabled for zephyr
        when(enabledTransports.isTcpEnabled()).thenReturn(false);
        when(enabledTransports.isJingleEnabled()).thenReturn(false);
        when(enabledTransports.isZephyrEnabled()).thenReturn(true);

        Transports transports = new Transports(
                _absRTRoot,
                _localUser,
                _localDID,
                _scrypted,
                _cfgLolol,
                enabledTransports,
                _coreQueue,
                _tc,
                _maxcastFilterReceiver,
                _linkStateService,
                null,
                _rockLog,
                _clientSslEngineFactory,
                _serverSSLEngineFactory,
                _clientSocketChannelFactory,
                _serverSocketChannelFactory);

        Collection<ITransport> constructedTransports = transports.getAll_();

        assertThat(constructedTransports, hasSize(1));
        assertThat(constructedTransports.iterator().next().id(), equalTo(TransportType.ZEPHYR.getId()));
        assertThat(constructedTransports.iterator().next().supportsMulticast(), equalTo(true));
    }

    // We need the suppresswarnings for the containsInAnyOrder assertion.
    @SuppressWarnings({"unchecked"})
    @Test
    public void shouldDisableMulticastForZephyrWhenJingleIsEnabled()
            throws ExUnsupportedTransport
    {
        CfgEnabledTransports enabledTransports = mock(CfgEnabledTransports.class);

        // disable every transport other than zephyr
        // even in this configuration multicast should be enabled for zephyr
        when(enabledTransports.isTcpEnabled()).thenReturn(false);
        when(enabledTransports.isJingleEnabled()).thenReturn(true);
        when(enabledTransports.isZephyrEnabled()).thenReturn(true);

        Transports transports = new Transports(
                _absRTRoot,
                _localUser,
                _localDID,
                _scrypted,
                _cfgLolol,
                enabledTransports,
                _coreQueue,
                _tc,
                _maxcastFilterReceiver,
                _linkStateService,
                null,
                _rockLog,
                _clientSslEngineFactory,
                _serverSSLEngineFactory,
                _clientSocketChannelFactory,
                _serverSocketChannelFactory);

        Collection<ITransport> constructedTransports = transports.getAll_();

        assertThat(constructedTransports, hasSize(2));

        // noinspection unchecked
        assertThat(constructedTransports, containsInAnyOrder(instanceOf(Jingle.class), instanceOf(Zephyr.class)));

        for (ITransport transport : constructedTransports) {
            if (transport instanceof Zephyr) {
                assertThat(transport.supportsMulticast(), equalTo(false));
            } else {
                assertThat(transport.supportsMulticast(), equalTo(true));
            }
        }
    }
}
