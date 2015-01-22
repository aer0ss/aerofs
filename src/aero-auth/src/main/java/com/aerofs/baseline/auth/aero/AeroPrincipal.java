package com.aerofs.baseline.auth.aero;

import com.google.common.base.Objects;

import javax.annotation.Nullable;
import java.security.Principal;

/**
 * A specialization of {@link Principal} that
 * identifies an AeroFS device.
 */
public final class AeroPrincipal implements Principal {

    private final String user;

    private final String device;

    private final String provenance;

    AeroPrincipal(String user, String device, String provenance) {
        this.user = user;
        this.device = device;
        this.provenance = provenance;
    }

    @Override
    public String getName() {
        return user;
    }

    public String getDevice() {
        return device;
    }

    public String getProvenance() {
        return provenance;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AeroPrincipal other = (AeroPrincipal) o;
        return Objects.equal(provenance, other.provenance) && Objects.equal(user, other.user) && Objects.equal(device, other.device);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(provenance, user, device);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("user", user)
                .add("device", device)
                .add("provenance", provenance)
                .toString();
    }
}
