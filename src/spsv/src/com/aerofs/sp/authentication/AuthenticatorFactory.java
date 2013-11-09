/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.LibParam.Identity.Authenticator;

/**
 * Create an IAuthenticator implementation that is appropriate for the current config
 * parameters from LibParam.
 *
 * Today this will create one of the following:
 *
 *  - a local authenticator (compare scrypt'ed password against sp database)
 *
 *  - an LDAP authenticator (pass raw credential to an external LDAP server).
 *    NOTE the LDAP authenticator supports auto-provisioning.
 *
 *  - a switching authenticator that uses the LDAP auth for internal users and the local
 *    authenticator for externals.
 */
public class AuthenticatorFactory
{
    public static IAuthenticator create()
    {
        if (Identity.AUTHENTICATOR == Authenticator.EXTERNAL_CREDENTIAL) {
            LdapConfiguration ldapConf = new LdapConfiguration();
            LdapAuthenticator ldapAuth = new LdapAuthenticator(ldapConf);

            return new SwitchingAuthenticator(ldapAuth, new LocalAuthenticator());
        } else {
            return new LocalAuthenticator();
        }
    }
}
