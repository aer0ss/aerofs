package com.aerofs.auth.server.delegated;

/**
 * Server-side headers, constants and functions specific to
 * the "Aero-Delegated-User-Device" HTTP authentication scheme.
 */
public abstract class AeroDelegatedUserDevice {

    /**
     * Name assigned to this authentication scheme.
     * This is also the scheme name used in the "Authorization" header.
     */
    public static final String AUTHENTICATION_SCHEME = "Aero-Delegated-User-Device";

    /**
     * Regex pattern the "Authorization" header value should have
     * for the "Aero-Delegated-User-Device" authentication scheme.
     */
    public static final String AERO_DELEGATED_USER_DEVICE_HEADER_REGEX = "Aero-Delegated-User-Device ([a-zA-Z0-9\\-_]+) ([0-9a-fA-F]{32}) ([a-zA-Z0-9=\\+\\/]+) ([0-9a-fA-F]{32})";

    private AeroDelegatedUserDevice() {
        // to prevent instantiation by subclasses
    }
}
