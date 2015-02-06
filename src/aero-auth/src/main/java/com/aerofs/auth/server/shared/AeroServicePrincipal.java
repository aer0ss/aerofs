package com.aerofs.auth.server.shared;

import com.aerofs.auth.server.AeroPrincipal;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

/**
 * Identifies the AeroFS service in <strong>this</strong>
 * AeroFS deployment that made the request.
 */
public final class AeroServicePrincipal implements AeroPrincipal {

    private final String service;

    /**
     * Constructor.
     *
     * @param service name of the service that made the request
     */
    public AeroServicePrincipal(String service) {
        this.service = service;
    }

    /**
     * Get the service name that made the request.
     *
     * @return service name that made the request
     *
     * @see #getService()
     */
    @Override
    public String getName() {
        return service;
    }

    /**
     * Get the service name that made the request.
     *
     * @return service name that made the request
     */
    public String getService() {
        return service;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AeroServicePrincipal other = (AeroServicePrincipal) o;
        return Objects.equal(service, other.service);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(service);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("service", service)
                .toString();
    }
}
