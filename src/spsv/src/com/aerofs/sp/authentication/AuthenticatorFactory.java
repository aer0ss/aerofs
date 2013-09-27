/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.lib.FullName;
import com.aerofs.sp.server.lib.user.User;

/**
 * Create an IAuthenticator implementation that is appropriate for the current config
 * parameters from LibParam.
 *
 * Today this will create a local authenticator (compare scrypt'ed password against sp database)
 * or an LDAP authenticator (pass raw credential to an external LDAP server).
 * The LDAP authenticator may support auto-provisioning.
 */
public class AuthenticatorFactory
{
    public static IAuthenticator create(LdapConfiguration ldap)
    {
        if (LdapConfiguration.enabled()) {
            return new LdapAuthenticator(ldap, ldap.SERVER_AUTOPROVISION ?
                        new AutoProvisioning() : new NoProvisioning());
        } else {
            return new LocalAuthenticator();
        }
    }

    static class NoProvisioning implements IProvisioningStrategy
    {
        @Override
        public void saveUser(User user, FullName fullName, byte[] credential) throws Exception
        {
            throw new ExBadCredential("User does not exist");
        }
    }

    static class AutoProvisioning implements IProvisioningStrategy
    {
        @Override
        public void saveUser(User user, FullName fullName, byte[] credential) throws Exception
        {
            // This user can be auto-provisioned with an external credential authenticator.
            // save the user record in the database with an empty password (which cannot
            // be used to sign in)
            user.save(new byte[0], fullName);
        }
    }
}
