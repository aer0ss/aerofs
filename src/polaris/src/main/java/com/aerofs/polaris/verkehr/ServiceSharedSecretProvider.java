package com.aerofs.polaris.verkehr;

import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.verkehr.client.rest.AuthorizationHeaderProvider;

import javax.inject.Inject;
import javax.inject.Named;

import static com.aerofs.baseline.Constants.SERVICE_NAME_INJECTION_KEY;
import static com.aerofs.polaris.Constants.DEPLOYMENT_SECRET_INJECTION_KEY;

public final class ServiceSharedSecretProvider implements AuthorizationHeaderProvider {

    private final String serviceName;
    private final String deploymentSecret;

    @Inject
    public ServiceSharedSecretProvider(@Named(SERVICE_NAME_INJECTION_KEY) String serviceName, @Named(DEPLOYMENT_SECRET_INJECTION_KEY) String deploymentSecret) {
        this.serviceName = serviceName;
        this.deploymentSecret = deploymentSecret;
    }

    @Override
    public String getAuthorizationHeaderValue() {
        return AeroService.getHeaderValue(serviceName, deploymentSecret);
    }
}
