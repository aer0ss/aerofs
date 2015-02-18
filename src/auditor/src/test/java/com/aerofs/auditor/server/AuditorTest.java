/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.baseline.metrics.MetricRegistries;
import com.aerofs.testlib.AbstractTest;
import com.jayway.restassured.RestAssured;
import org.junit.Before;
import org.junit.Rule;

import static com.jayway.restassured.config.RedirectConfig.redirectConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;

public class AuditorTest extends AbstractTest
{
    protected static final String AUDIT_URL = "/event";

    @Rule
    public AuditorTestServer _service = new AuditorTestServer();

    @Before
    public void setUp()
            throws Throwable
    {
        try {
            RestAssured.baseURI = "http://localhost";
            RestAssured.port = _service.getPort();
            RestAssured.config = newConfig().redirect(redirectConfig().followRedirects(false));
        } catch (Throwable t) {
            MetricRegistries.unregisterMetrics();
            throw t;
        }
    }
}
