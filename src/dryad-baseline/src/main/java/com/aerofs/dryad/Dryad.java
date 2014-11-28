package com.aerofs.dryad;

import com.aerofs.baseline.AdminEnvironment;
import com.aerofs.baseline.RootEnvironment;
import com.aerofs.baseline.Service;
import com.aerofs.baseline.ServiceEnvironment;
import com.aerofs.dryad.config.DryadConfiguration;
import com.aerofs.dryad.resources.LogsResource;
import com.aerofs.dryad.store.FileStore;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

public final class Dryad extends Service<DryadConfiguration> {

    public static void main(String[] args) throws Exception {
        Dryad dryad = new Dryad();
        dryad.run(args);
    }

    public Dryad() {
        super("dryad");
    }

    @Override
    public void init(DryadConfiguration configuration, RootEnvironment root, AdminEnvironment admin, ServiceEnvironment service) throws Exception {
        // configure the service injector
        service.addProvider(new AbstractBinder() {
            final FileStore fileStore = new FileStore(configuration.getStorageDirectory());

            @Override
            protected void configure() {
                bind(fileStore).to(FileStore.class);
            }
        });

        // configure mappers and resources
        service.addProvider(BlacklistedExceptionMapper.class);
        service.addResource(LogsResource.class);
    }
}
