/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.auditor.downstream.Downstream;
import com.aerofs.base.Loggers;
import com.aerofs.baseline.AdminEnvironment;
import com.aerofs.baseline.RootEnvironment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.ServiceEnvironment;
import com.aerofs.baseline.auth.aero.AeroClientCertBasedAuthenticator;
import com.aerofs.baseline.auth.aero.AeroPrincipalBinder;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import static com.google.common.base.Preconditions.checkState;

public class Auditor extends Service<AuditorConfiguration>
{
    static
    {
        Loggers.init();
    }

    public static void main(String[] args)
            throws Exception
    {
        Auditor auditor = new Auditor();
        auditor.setDownstream(Downstream.create());
        auditor.run(args);
    }

    private Downstream.AuditChannel _downstream;

    public Auditor()
    {
        super("auditor");
    }

    // exposed for test purposes only
    void setDownstream(Downstream.AuditChannel downstream)
    {
        checkState(_downstream == null, "downstream already set:%s", _downstream);
        this._downstream = downstream;
    }

    @Override
    public void init(AuditorConfiguration configuration, RootEnvironment root, AdminEnvironment admin, ServiceEnvironment service)
            throws Exception
    {
        checkState(_downstream != null, "downstream not set");

        root.addAuthenticator(new AeroClientCertBasedAuthenticator());
        root.addAuthenticator(new LegacyAuthenticator());

        service.addProvider(new AeroPrincipalBinder());
        service.addProvider(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(_downstream).to(Downstream.AuditChannel.class);
            }
        });

        service.addResource(EventResource.class);
    }
}
