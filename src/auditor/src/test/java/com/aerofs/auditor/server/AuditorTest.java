/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.auditor.downstream.Downstream.AuditChannel;
import com.aerofs.baseline.AdminEnvironment;
import com.aerofs.baseline.RootEnvironment;
import com.aerofs.baseline.ServiceEnvironment;
import com.aerofs.baseline.config.Configuration;
import com.aerofs.testlib.AbstractTest;
import com.google.common.io.Resources;
import com.jayway.restassured.RestAssured;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.junit.After;
import org.junit.Before;

import java.io.FileInputStream;

import static com.jayway.restassured.config.RedirectConfig.redirectConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;

public class AuditorTest extends AbstractTest
{
    protected final static String AUDIT_URL = "/event";

    protected static class WhiteBoxedDownstream implements AuditChannel
    {
        protected Exception _failureCause = null;

        @Override
        public ChannelFuture doSend(String message)
        {
            ChannelFuture future = new DefaultChannelFuture(null, false);

            if (_failureCause == null) {
                future.setSuccess();
            } else {
                future.setFailure(_failureCause);
            }

            return future;
        }

        @Override
        public boolean isConnected()
        {
            return true;
        }
    }

    protected static class TestAuditor extends Auditor
    {
        protected final WhiteBoxedDownstream _downstream = new WhiteBoxedDownstream();

        @Override
        public void init(AuditorConfiguration configuration, RootEnvironment root, AdminEnvironment admin, ServiceEnvironment service)
                throws Exception
        {
            super.init(configuration, root, admin, service);

            service.addProvider(new AbstractBinder()
            {
                @Override
                protected void configure()
                {
                    // hk2 prefers objects bound *earlier* over those bound later
                    // to override this default we explicitly rank this implementation higher than the default '0'
                    bind(_downstream).to(AuditChannel.class).ranked(1);
                }
            });
        }
    }

    private static final class ConfigurationReference
    {
        private static final String TEST_CONFIGURATION_FILENAME = "auditor_test_server.yml";
        private static AuditorConfiguration CONFIGURATION = load();

        private static AuditorConfiguration load()
        {
            try(FileInputStream in = new FileInputStream(Resources.getResource(TEST_CONFIGURATION_FILENAME).getFile())) {
                return Configuration.loadYAMLConfigurationFromStream(Auditor.class, in);
            } catch (Exception e) {
                throw new RuntimeException("failed to load configuration", e);
            }
        }
    }

    TestAuditor _service;
    protected int _port;

    @Before
    public void setUp() throws Exception
    {
        _service = new TestAuditor();
        _service.runWithConfiguration(ConfigurationReference.CONFIGURATION);
        _port = ConfigurationReference.CONFIGURATION.getService().getPort();
        _service._downstream._failureCause = null;

        RestAssured.baseURI = "http://localhost";
        RestAssured.port = _port;
        RestAssured.config = newConfig().redirect(redirectConfig().followRedirects(false));
    }

    @After
    public void tearDown()
    {
        _service.shutdown();
    }
}
