/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.lib.LibParam.Identity;

/**
 * Create and configure an Authenticator instance for the current configured identity authorities.
 */
public class AuthenticatorFactory
{
    public static Authenticator create()
    {
        IAuthority[] authorities;
        if (Identity.AUTHENTICATOR == Identity.Authenticator.EXTERNAL_CREDENTIAL) {
            authorities = new IAuthority[] { new LdapAuthority(new LdapConfiguration()),
                                             new LocalAuthority() };
        } else if (Identity.AUTHENTICATOR == Identity.Authenticator.OPENID) {
            authorities = new IAuthority[] {new OpenIdAuthority(), new LocalAuthority() };
        } else {
            authorities = new IAuthority[] { new LocalAuthority() };
        }
        return new Authenticator(authorities);
    }
}
