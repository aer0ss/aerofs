package com.aerofs.trifrost;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.baseline.Environment;
import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.baseline.metrics.MetricRegistries;
import com.aerofs.proto.Common;
import com.aerofs.servlets.lib.AbstractEmailSender;
import com.aerofs.trifrost.api.VerifiedDevice;
import com.aerofs.trifrost.base.UniqueIDGenerator;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;

import javax.mail.Message;
import javax.mail.Session;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

public final class TrifrostTestResource extends ExternalResource {

    private final class TrifrostTestServer extends Trifrost {
        protected TrifrostTestServer(String name) {
            super(name);
        }

        @Override
        public void init(TrifrostConfiguration configuration, Environment environment) throws Exception {
            super.init(configuration, environment);

            environment.addBinder(new AbstractBinder() {
                @Override
                protected void configure() {
                    bind(new MockMailSender()).to(AbstractEmailSender.class).ranked(1);
                    bind(new MockUniqueID()).to(UniqueIDGenerator.class).ranked(1);
                    bind(new MockSparta()).to(ISpartaClient.class).ranked(1);
                }
            });
        }

        @Override
        protected void initConfigProperties() {
            Properties testProperties = new Properties();
            testProperties.setProperty("labeling.brand", "Big Brother");
            testProperties.setProperty("base.www.support_email_address", "bro@test.co");

            ConfigurationProperties.setProperties(testProperties);
        }

        @Override
        protected String getDeploymentSecret(TrifrostConfiguration configuration) throws IOException {
            return "aa23e7fb907fa7f839f6f418820159ab";
        }
    }

    private class MockSparta implements ISpartaClient {
        @Override
        public VerifiedDevice getTokenForUser(String principal) throws IOException {
            VerifiedDevice result = new VerifiedDevice();
            result.accessTokenExpiration = 0;
            result.accessToken = "aa23e7fb907fa7f839f6f418820159ab";
            return result;
        }
    }

    private class MockMailSender extends AbstractEmailSender
    {
        public MockMailSender() {
            super("", "", "", "", true, "");
        }

        @Override
        protected Future<Common.Void> sendMessage(final Message msg, final Session session)
                throws RejectedExecutionException
        {
            System.out.println("Mail was requested..." + msg.toString());
            return null;
        }
    }

    private class MockUniqueID implements UniqueIDGenerator {
        @Override
        public char[] generateOneTimeCode() {
            return new char[] {'1', '2', '3', '4', '5', '6'};
        }

        @Override
        public char[] generateDeviceString() { return UniqueIDGenerator.create().generateDeviceString(); }
    }

    private Trifrost profileServer;

    public static RuleChain toRuleChain() {
        return RuleChain.outerRule(new MySQLDatabase("trifrost")).around(new TrifrostTestResource());
    }

    @Override
    protected void before() throws Throwable {
        InputStream resourceAsStream = ClassLoader.getSystemResourceAsStream("trifrost_test.yml");
        TrifrostConfiguration configuration = Configuration.loadYAMLConfigurationFromStream(Trifrost.class, resourceAsStream);
        profileServer = new TrifrostTestServer("profile");
        try {
            profileServer.runWithConfiguration(configuration);
        } catch (Throwable t) {
            MetricRegistries.unregisterMetrics();
            throw t;
        }
    }

    @Override
    protected void after() {
        profileServer.shutdown();
    }
}
