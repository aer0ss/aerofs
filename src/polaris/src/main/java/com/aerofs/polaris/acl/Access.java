package com.aerofs.polaris.acl;

/**
 * Access types that can be requested for an object.
 */
public enum Access {

    /**
     * Query the current or previous state of the object.
     */
    READ,

    /**
     * Modify the current state of the object.
     */
    WRITE
}
