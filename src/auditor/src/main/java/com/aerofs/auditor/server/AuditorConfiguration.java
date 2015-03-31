package com.aerofs.auditor.server;

import com.aerofs.baseline.config.Configuration;
import org.hibernate.validator.constraints.NotBlank;

public class AuditorConfiguration extends Configuration
{
    // FIXME (AG): specify downstream configuration here
    @NotBlank
    private String deploymentSecretPath = "/data/deployment_secret";

    public String getDeploymentSecretPath() {
        return deploymentSecretPath;
    }

    public void setDeploymentSecretPath(String deploymentSecretPath) {
        this.deploymentSecretPath = deploymentSecretPath;
    }
}
