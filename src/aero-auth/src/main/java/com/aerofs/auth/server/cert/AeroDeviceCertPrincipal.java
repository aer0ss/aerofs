package com.aerofs.auth.server.cert;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.google.common.base.Objects;

import javax.annotation.Nullable;

/**
 * Identifies the AeroFS device that made the request.
 */
public final class AeroDeviceCertPrincipal implements AeroUserDevicePrincipal {

    private final String user;
    private final String device;

    /**
     * Constructor.
     *
     * @param user user id of the user that made the request
     * @param device DID (32 character hex string) of the device that made the request
     */
    public AeroDeviceCertPrincipal(String user, String device) {
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

        AeroDeviceCertPrincipal other = (AeroDeviceCertPrincipal) o;
        return Objects.equal(user, other.user) && Objects.equal(device, other.device);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(user, device);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("user", user)
                .add("device", device)
                .toString();
    }
}
