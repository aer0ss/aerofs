/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.resource;

import com.aerofs.auditor.server.Auditor;
import com.aerofs.testlib.AbstractTest;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.jayway.restassured.RestAssured;
import org.junit.After;
import org.junit.Before;

import static com.jayway.restassured.config.RedirectConfig.redirectConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;

public class AuditorTest extends AbstractTest
{
    Auditor _service;
    protected int _port;
    private Injector _injector;

    protected final static String AUDIT_URL = "/event";

    @Before
    public void setUp() throws Exception
    {
        _injector = Guice.createInjector(Auditor.auditorModule());

        _service = new Auditor(_injector, null);
        _service.start();
        _port = _service.getListeningPort();

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
