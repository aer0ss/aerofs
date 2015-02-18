package com.aerofs.auth.server;

import com.google.common.base.Objects;

import javax.annotation.Nullable;

/**
 * Represents a service-wide shared deployment secret.
 */
public final class SharedSecret {

    private final String deploymentSecret;

    /**
     * Constructor.
     *
     * @param deploymentSecret a deployment secret shared between all services in
     *                         <strong>this</strong> AeroFS installation
     */
    public SharedSecret(String deploymentSecret) {
        this.deploymentSecret = deploymentSecret;
    }

    /**
     * Constant-time equality check between this object
     * and {@code compared} - the string representation of
     * a shared secret.
     *
     * @param compared string representation of a secret shared
     *                 between all services in <strong>this</strong>
     *                 AeroFS installation
     * @return {@code true} if {@code compared} matches this deployment's shared secret, {@code false} otherwise
     */
    public boolean equalsString(String compared) {
        if (deploymentSecret.length() != compared.length()) {
            return false;
        }

        int equality = 0;

        for (int i = 0 ; i < deploymentSecret.length(); i++) {
            equality |= deploymentSecret.charAt(i) ^ compared.charAt(i);
        }

        return equality == 0;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SharedSecret other = (SharedSecret) o;
        return Objects.equal(deploymentSecret, other.deploymentSecret);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(deploymentSecret);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("deploymentSecret", deploymentSecret)
                .toString();
    }
}
