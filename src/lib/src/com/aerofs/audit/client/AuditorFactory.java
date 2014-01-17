/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.audit.client;

import com.aerofs.base.BaseParam.Audit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Factory to create IAuditorClient instances.
 * TODO: implement local and remote http client configurations
 * TODO: implement test auditor client?
 */
public class AuditorFactory
{
    private static Logger l = LoggerFactory.getLogger(AuditorFactory.class);

    public static IAuditorClient create()
    {
        if (Audit.AUDIT_ENABLED) {
            try {
                l.info("Enabling connection to audit server at {}:{}/{}",
                        Audit.SERVICE_HOST, Audit.SERVICE_PORT, Audit.SERVICE_EVENT_PATH);
                return new AuditHttpClient(new URL(
                        "http", Audit.SERVICE_HOST, Audit.SERVICE_PORT, Audit.SERVICE_EVENT_PATH));
            } catch (MalformedURLException mue) {
                l.error("Misconfigured audit service URL. Auditing is DISABLED.");
            }
        }
        l.info("Audit service is disabled.");

        return new IAuditorClient()
        {
            @Override
            public void submit(String content) throws IOException { }
        };
    }
}
