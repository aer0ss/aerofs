package com.aerofs.auditor.server;

import com.aerofs.auditor.downstream.Downstream;
import com.aerofs.baseline.Environment;
import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.metrics.MetricRegistries;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.junit.rules.ExternalResource;

import javax.annotation.Nullable;

@SuppressWarnings("unused")
public final class AuditorTestServer extends ExternalResource
{
    private static final AuditorConfiguration CONFIGURATION = Configuration.loadYAMLConfigurationFromResourcesUncheckedThrow(Auditor.class, "auditor_test_server.yml");

    private static final class WhiteBoxedDownstream implements Downstream.AuditChannel
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
        public void init(AuditorConfiguration configuration, Environment environment)
                throws Exception
        {
            super.init(configuration, environment);

            environment.addServiceProvider(new AbstractBinder()
            {
                @Override
                protected void configure()
                {
                    // hk2 prefers objects bound *earlier* over those bound later
                    // to override this default we explicitly rank this implementation higher than the default '0'
                    bind(_downstream).to(Downstream.AuditChannel.class).ranked(1);
                }
            });
        }
    }

    private final TestAuditor _auditor = new TestAuditor();

    @Override
    protected void before() throws Throwable {
        super.before();

        try {
            _auditor.runWithConfiguration(CONFIGURATION);
            _auditor._downstream._failureCause = null;
        } catch (Throwable t) {
            MetricRegistries.unregisterMetrics();
            throw t;
        }
    }

    @Override
    protected void after() {
        try {
            _auditor.shutdown();
        } finally {
            super.after();
        }
    }

    public int getPort() {
        return CONFIGURATION.getService().getPort();
    }

    public Exception getDownstreamFailureCause() {
        return _auditor._downstream._failureCause;
    }

    public void setDownstreamFailureCause(@Nullable Exception cause) {
        _auditor._downstream._failureCause = cause;
    }
}
