/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.auditor.downstream.Downstream;
import com.aerofs.auth.server.AeroPrincipalBinder;
import com.aerofs.auth.server.cert.AeroDeviceCertAuthenticator;
import com.aerofs.baseline.Environment;
import com.aerofs.baseline.Service;
import com.aerofs.lib.properties.ServerConfigurationLoader;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

public class Auditor extends Service<AuditorConfiguration>
{
    public static void main(String[] args)
            throws Exception
    {
        ServerConfigurationLoader.initialize("auditor");
        Auditor auditor = new Auditor();
        auditor.run(args);
    }

    public Auditor()
    {
        super("auditor");
    }

    @Override
    public void init(AuditorConfiguration configuration, Environment environment)
            throws Exception
    {
        environment.addAuthenticator(new AeroDeviceCertAuthenticator());
        environment.addAuthenticator(new LegacyAuthenticator());

        environment.addServiceProvider(new AeroPrincipalBinder());
        environment.addServiceProvider(new AbstractBinder()
        {
            @Override
            protected void configure()
            {
                bind(Downstream.create()).to(Downstream.AuditChannel.class);
            }
        });

        environment.addResource(EventResource.class);
    }
}
