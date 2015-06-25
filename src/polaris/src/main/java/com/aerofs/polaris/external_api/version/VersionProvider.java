package com.aerofs.polaris.external_api.version;

import com.aerofs.polaris.external_api.rest.util.Version;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.inject.Singleton;

public class VersionProvider extends AbstractBinder {

    @Override
    protected void configure() {
        bindFactory(VersionFactory.class).to(Version.class).in(Singleton.class);
    }
}
