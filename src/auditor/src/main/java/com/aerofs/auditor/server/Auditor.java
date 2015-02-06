/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.auditor.downstream.Downstream;
import com.aerofs.auth.server.AeroPrincipalBinder;
import com.aerofs.auth.server.cert.AeroDeviceCertAuthenticator;
import com.aerofs.baseline.AdminEnvironment;
import com.aerofs.baseline.RootEnvironment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.ServiceEnvironment;
import com.aerofs.lib.properties.Configuration;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

public class Auditor extends Service<AuditorConfiguration>
{
    public static void main(String[] args)
            throws Exception
    {
        Configuration.Server.initialize();
        Auditor auditor = new Auditor();
        auditor.run(args);
    }

    public Auditor()
    {
        super("auditor");
    }

    @Override
    public void init(AuditorConfiguration configuration, RootEnvironment root, AdminEnvironment admin, ServiceEnvironment service)
            throws Exception
    {
        root.addAuthenticator(new AeroDeviceCertAuthenticator());
        root.addAuthenticator(new LegacyAuthenticator());

        service.addProvider(new AeroPrincipalBinder());
        service.addProvider(new AbstractBinder()
        {
            @Override
            protected void configure()
            {
                bind(Downstream.create()).to(Downstream.AuditChannel.class);
            }
        });

        service.addResource(EventResource.class);
    }
}
