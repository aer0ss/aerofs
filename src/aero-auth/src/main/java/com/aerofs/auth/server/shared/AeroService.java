package com.aerofs.auth.server.shared;

/**
 * Server-side headers, constants and functions
 * specific to the "Aero-Service-Shared-Secret"
 * HTTP authentication scheme.
 */
public abstract class AeroService {

    /**
     * Name assigned to this authentication scheme.
     * This is also the scheme name used in the "Authorization" header.
     */
    public static final String AUTHENTICATION_SCHEME = "Aero-Service-Shared-Secret";

    /**
     * Regex pattern the "Authorization" header value should have
     * for the "Aero-Service-Shared-Secret" authentication scheme.
     */
    public static final String AERO_SERVICE_SHARED_SECRET_HEADER_REGEX = "Aero-Service-Shared-Secret ([a-zA-Z0-9\\-_]+) ([0-9a-fA-F]{32})";

    private AeroService() {
        // to prevent instantiation by subclasses
    }
}
