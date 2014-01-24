/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.rest.util;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.MDID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.oauth.VerifyTokenResponse;

import java.util.Set;

public class OAuthToken
{
    public final UserID user;
    public final MDID did;
    public final OrganizationID org;
    public final Set<String> scopes;

    public OAuthToken(VerifyTokenResponse response) throws ExFormatError
    {
        user = response.principal.getUserID();
        did = new MDID(UniqueID.fromStringFormal(response.mdid));
        org = response.principal.getOrganizationID();
        scopes = response.scopes;
    }

    public boolean isAllowedToWrite()
    {
        return scopes.contains("write");
    }
}
