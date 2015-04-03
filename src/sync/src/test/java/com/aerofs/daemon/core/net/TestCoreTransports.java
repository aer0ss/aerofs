/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.ids.DID;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.net.TransportFactory.ExUnsupportedTransport;
import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ConfigurationPropertiesResource;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.testlib.LoggerSetup;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.lib.cfg.CfgAbsRTRoot;
import com.aerofs.lib.cfg.CfgEnabledTransports;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgLolol;
import com.aerofs.lib.cfg.CfgScrypted;
import com.aerofs.testlib.AbstractTest;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.Timer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;

import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestCoreTransports extends AbstractTest
{
    static
    {
        LoggerSetup.init();
    }

    private @Mock CfgAbsRTRoot _absRTRoot;
    private @Mock CfgLocalUser _localUser;
    private @Mock CfgLocalDID _localDID;
    private @Mock CfgScrypted _scrypted;
    private @Mock CfgLolol _cfgLolol;
    private @Mock Timer _timer;
    private @Mock CoreQueue _coreQueue;
    private @Mock MaxcastFilterReceiver _maxcastFilterReceiver;
    private @Mock LinkStateService _linkStateService;
    private @Mock ClientSSLEngineFactory _clientSslEngineFactory;
    private @Mock ServerSSLEngineFactory _serverSSLEngineFactory;
    private @Mock ClientSocketChannelFactory _clientSocketChannelFactory;
    private @Mock ServerSocketChannelFactory _serverSocketChannelFactory;
    private @Mock IRoundTripTimes _roundTripTimes;

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
    public void shouldEnableMulticastForZephyr()
            throws ExUnsupportedTransport
    {
        CfgEnabledTransports enabledTransports = mock(CfgEnabledTransports.class);

        // disable every transport other than zephyr
        // even in this configuration multicast should be enabled for zephyr
        when(enabledTransports.isTcpEnabled()).thenReturn(false);
        when(enabledTransports.isZephyrEnabled()).thenReturn(true);

        Transports transports = new Transports(
                _localUser,
                _localDID,
                _scrypted,
                enabledTransports,
                _timer,
                _coreQueue,
                _maxcastFilterReceiver,
                _linkStateService,
                _clientSslEngineFactory,
                _serverSSLEngineFactory,
                _clientSocketChannelFactory,
                _serverSocketChannelFactory,
                _roundTripTimes);

        Collection<ITransport> constructedTransports = transports.getAll_();

        assertThat(constructedTransports, hasSize(1));
        assertThat(constructedTransports.iterator().next().id(), equalTo(TransportType.ZEPHYR.getId()));
        assertThat(constructedTransports.iterator().next().supportsMulticast(), equalTo(true));
    }
}
