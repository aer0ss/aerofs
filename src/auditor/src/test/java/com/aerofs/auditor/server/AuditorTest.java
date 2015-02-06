/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.auditor.downstream.Downstream.AuditChannel;
import com.aerofs.baseline.AdminEnvironment;
import com.aerofs.baseline.RootEnvironment;
import com.aerofs.baseline.ServiceEnvironment;
import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.metrics.MetricRegistries;
import com.aerofs.testlib.AbstractTest;
import com.jayway.restassured.RestAssured;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.junit.After;
import org.junit.Before;

import static com.jayway.restassured.config.RedirectConfig.redirectConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;

public class AuditorTest extends AbstractTest
{
    private static final AuditorConfiguration CONFIGURATION = Configuration.loadYAMLConfigurationFromResourcesUncheckedThrow(Auditor.class, "auditor_test_server.yml");

    private static final class WhiteBoxedDownstream implements AuditChannel
    {
        private Exception _failureCause = null;

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

    private static final class TestAuditor extends Auditor
    {
        private final WhiteBoxedDownstream _downstream = new WhiteBoxedDownstream();

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

    protected static final String AUDIT_URL = "/event";
    protected final TestAuditor _service = new TestAuditor();

    @Before
    public void setUp()
            throws Throwable
    {
        try {
            _service.runWithConfiguration(CONFIGURATION);
            _service._downstream._failureCause = null;

            RestAssured.baseURI = "http://localhost";
            RestAssured.port = CONFIGURATION.getService().getPort();
            RestAssured.config = newConfig().redirect(redirectConfig().followRedirects(false));
        } catch (Throwable t) {
            MetricRegistries.unregisterMetrics();
            throw t;
        }
    }

    @After
    public void tearDown()
    {
        _service.shutdown();
    }

    @SuppressWarnings("unused")
    protected final Exception getDownstreamFailureCause()
    {
        return _service._downstream._failureCause;
    }

    protected final void setDownstreamFailureCause(Exception cause)
    {
        _service._downstream._failureCause = cause;
    }
}
