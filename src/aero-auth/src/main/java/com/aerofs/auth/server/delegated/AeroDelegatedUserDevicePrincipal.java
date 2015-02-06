package com.aerofs.auth.server.delegated;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

/**
 * Identifies an AeroFS device that made the request
 * <strong>via an intermediate</strong> AeroFS backend
 * service.
 */
public final class AeroDelegatedUserDevicePrincipal implements AeroUserDevicePrincipal {

    private final String service;
    private final String user;
    private final String device;

    /**
     * Constructor.
     *
     * @param service name of the service <strong>through which</strong> the request was made
     * @param user user id of the user that made the request
     * @param device DID (32 character hex string) of the device that made the request
     */
    public AeroDelegatedUserDevicePrincipal(String service, String user, String device) {
        this.service = service;
        this.user = user;
        this.device = device;
    }

    /**
     * Return the tuple {@code user:device} of the entity that made the request
     *
     * @return a tuple of the form {@code user:device} of the entity that made the request
     *
     * @see #getUser()
     * @see #getDevice()
     */
    @Override
    public String getName() {
        return String.format("%s:%s", user, device);
    }

    /**
     * Get the service name through which the user/device made the request
     *
     * @return service name through which the user/device made the request
     */
    public String getService() {
        return service;
    }

    @Override
    public String getUser() {
        return user;
    }

    @Override
    public String getDevice() {
        return device;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AeroDelegatedUserDevicePrincipal other = (AeroDelegatedUserDevicePrincipal) o;
        return Objects.equal(service, other.service) && Objects.equal(user, other.user) && Objects.equal(device, other.device);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(service, user, device);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("service", service)
                .add("user", user)
                .add("device", device)
                .toString();
    }
}
