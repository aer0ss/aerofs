package com.aerofs.auth.server;

/**
 * Roles various {@link AeroPrincipal} entities have.
 */
public abstract class Roles {

    public static final String SYSTEM = "sys";

    /**
     * Identifies a request made by an AeroFS desktop or mobile client, or one made by a user via the web frontend.
     */
    public static final String USER = "usr";

    /**
     * Identifies a request made by an AeroFS frontend or backend service.
     */
    public static final String SERVICE = "svc";

    private Roles() {
        // to prevent instantiation by subclasses
    }
}
