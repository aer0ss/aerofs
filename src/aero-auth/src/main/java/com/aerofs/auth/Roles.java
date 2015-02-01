package com.aerofs.auth;

public abstract class Roles {

    public static final String SYSTEM = "sys";

    public static final String USER = "usr";

    public static final String SERVICE = "svc";

    private Roles() {
        // to prevent instantiation by subclasses
    }
}
