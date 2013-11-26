/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.client;

public interface IBadCredentialListener
{
    /**
     * IBadCredentialListeners are attached to SPBlockingClient to detect failed logins.
     * On a failed login, exceptionReceived will be called.
     */
    void exceptionReceived();
}
