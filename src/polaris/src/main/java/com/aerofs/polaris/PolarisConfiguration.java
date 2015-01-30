package com.aerofs.polaris;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.polaris.sparta.SpartaConfiguration;
import com.aerofs.polaris.verkehr.VerkehrConfiguration;
import com.google.common.base.Objects;
import org.hibernate.validator.constraints.NotBlank;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@SuppressWarnings("unused")
public final class PolarisConfiguration extends Configuration {

    @NotBlank
    private String deploymentSecretPath = Constants.DEPLOYMENT_SECRET_ABSOLUTE_PATH;

    @Min(1)
    private int maxReturnedTransforms = Constants.MAX_RETURNED_TRANSFORMS;

    @NotNull
    @Valid
    private DatabaseConfiguration database;

    @NotNull
    @Valid
    private SpartaConfiguration sparta;

    @NotNull
    @Valid
    private VerkehrConfiguration verkehr;

    public String getDeploymentSecretPath() {
        return deploymentSecretPath;
    }

    public void setDeploymentSecretPath(String deploymentSecretPath) {
        this.deploymentSecretPath = deploymentSecretPath;
    }

    public int getMaxReturnedTransforms() {
        return maxReturnedTransforms;
    }

    public void setMaxReturnedTransforms(int maxReturnedTransforms) {
        this.maxReturnedTransforms = maxReturnedTransforms;
    }

    public DatabaseConfiguration getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfiguration database) {
        this.database = database;
    }

    public SpartaConfiguration getSparta() {
        return sparta;
    }

    public void setSparta(SpartaConfiguration sparta) {
        this.sparta = sparta;
    }

    public VerkehrConfiguration getVerkehr() {
        return verkehr;
    }

    public void setVerkehr(VerkehrConfiguration verkehr) {
        this.verkehr = verkehr;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        PolarisConfiguration other = (PolarisConfiguration) o;

        return maxReturnedTransforms == other.maxReturnedTransforms
                && Objects.equal(database, other.database)
                && Objects.equal(sparta, other.sparta)
                && Objects.equal(verkehr, other.verkehr);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + Objects.hashCode(maxReturnedTransforms, database, sparta, verkehr);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("maxReturnedTransforms", getMaxReturnedTransforms())
                .add("admin", getAdmin())
                .add("service", getService())
                .add("logging", getLogging())
                .add("database", getDatabase())
                .add("sparta", getSparta())
                .add("verkehr", getVerkehr())
                .toString();
    }
}
