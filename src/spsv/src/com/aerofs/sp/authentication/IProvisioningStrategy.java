/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.lib.FullName;
import com.aerofs.sp.server.lib.user.User;

/**
 * Interface to describe possible user-provisioning strategies. Current implementations include
 * auto-provisioning (create the user when they first sign in) or deny the provision request
 * (if the user does not exist they can not sign in).
 */
public interface IProvisioningStrategy
{
    // FIXME: consider a "ProvisioningData" object to include these params, it would be
    // a bit easier to maintain (though more code to create and submit).

    /**
     * Provision the user (or choose not to and throw an exception).
     *
     * Precondition: the user does not exist in the database.
     *
     * If provisioning is successful, this returns normally. Any implementation that does not
     * throw should complete updating the user in the database.
     *
     * This method expects to be called in the context of a database transaction.
     *
     * @throws Exception Provisioning could not complete (or should not complete due to policy).
     */
    public void saveUser(User user, FullName fullName, byte[] credential) throws Exception;
}
