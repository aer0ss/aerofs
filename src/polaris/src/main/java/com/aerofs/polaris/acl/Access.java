package com.aerofs.polaris.acl;

/**
 * Access types that can be requested for a shared folder.
 */
public enum Access {

    /**
     * Query the current or previous state of the shared folder.
     */
    READ,

    /**
     * Modify the current state of the shared folder.
     */
    WRITE
}
