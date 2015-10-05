package com.aerofs.trifrost;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.trifrost.base.Constants;
import org.hibernate.validator.constraints.NotBlank;

import javax.validation.Valid;

@SuppressWarnings("unused")
public final class TrifrostConfiguration extends Configuration {
    @Valid
    private DatabaseConfiguration database;
    @Valid
    private UnifiedPushConfiguration unifiedPush;

    @NotBlank
    private String deploymentSecretPath = Constants.DEPLOYMENT_SECRET_ABSOLUTE_PATH;

    @Valid
    private boolean swaggerEnabled = false;

    public DatabaseConfiguration getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfiguration database) {
        this.database = database;
    }

    public UnifiedPushConfiguration getUnifiedPush() {
        return unifiedPush;
    }

    public void setUnifiedPush(UnifiedPushConfiguration conf) {
        this.unifiedPush = conf;
    }

    public String getDeploymentSecretPath() {
        return deploymentSecretPath;
    }

    public void setDeploymentSecretPath(String deploymentSecretPath) {
        this.deploymentSecretPath = deploymentSecretPath;
    }

    public boolean isSwaggerEnabled() {
        return swaggerEnabled;
    }

    public void setSwaggerEnabled(boolean swaggerEnabled) {
        this.swaggerEnabled = swaggerEnabled;
    }
}
