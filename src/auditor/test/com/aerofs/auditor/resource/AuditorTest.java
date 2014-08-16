/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.resource;

import com.aerofs.auditor.server.Auditor;
import com.aerofs.auditor.server.AuditorConfiguration;
import com.aerofs.auditor.server.Downstream;
import com.aerofs.auditor.server.Downstream.IAuditChannel;
import com.aerofs.restless.Configuration;
import com.aerofs.testlib.AbstractTest;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.jayway.restassured.RestAssured;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.junit.After;
import org.junit.Before;

import static com.jayway.restassured.config.RedirectConfig.redirectConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;

public class AuditorTest extends AbstractTest
{
    Auditor _service;
    protected int _port;
    private Injector _injector;
    protected WhiteBoxedDownstream _downstream = new WhiteBoxedDownstream();

    protected final static String AUDIT_URL = "/event";

    static class WhiteBoxedDownstream implements IAuditChannel {
        public WhiteBoxedDownstream() { _failureCause = null; }

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
        public boolean isConnected() { return true; }
        public Exception _failureCause;
    }

    @Before
    public void setUp() throws Exception
    {
        _injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Configuration.class).to(AuditorConfiguration.class);
                bind(Downstream.IAuditChannel.class).toInstance(_downstream);
            }
        });

        _service = new Auditor(_injector);
        _service.start();
        _port = _service.getListeningPort();
        _downstream._failureCause = null;

        RestAssured.baseURI = "http://localhost";
        RestAssured.port = _port;
        RestAssured.config = newConfig().redirect(redirectConfig().followRedirects(false));
        l.info("Auditor service started at {}", RestAssured.port);
    }

    @After
    public void tearDown()
    {
        _service.stop();
    }
}
