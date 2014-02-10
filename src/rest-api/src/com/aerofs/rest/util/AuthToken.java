/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.rest.util;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.MDID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.oauth.VerifyTokenResponse;

import java.util.Set;

public class AuthToken
{
    public final UserID user;
    public final DID did;
    public final OrganizationID org;
    public final Set<String> scopes;

    public AuthToken(VerifyTokenResponse response) throws ExFormatError
    {
        user = response.principal.getUserID();
        did = new MDID(UniqueID.fromStringFormal(response.mdid));
        org = response.principal.getOrganizationID();
        scopes = response.scopes;
    }

    // TODO: cert-based auth

    public boolean isAllowedToWrite()
    {
        return scopes.contains("write");
    }
}
