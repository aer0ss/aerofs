/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.oauth;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Sp.AuthorizeMobileDeviceReply;
import com.aerofs.sp.client.SPBlockingClient;

import javax.inject.Inject;
import java.util.Collection;

public class PrincipalFactory
{
    @Inject
    private SPBlockingClient.Factory spFactory;

    public AuthenticatedPrincipal authenticate(
            String nonce, String devName, Collection<String> requestedScopes) throws Exception
    {
        AuthorizeMobileDeviceReply auth = spFactory.create().authorizeMobileDevice(nonce, devName);

        // is this a request for admin privilege; if so, throw if the caller is not allowed
        boolean isAdmin = isAdminRequest(requestedScopes);
        if (isAdmin && (!auth.getIsOrgAdmin())) {
            throw new ExNoPerm("User does not have admin privilege");
        }

        OrganizationID orgID = new OrganizationID(Integer.valueOf(auth.getOrgId()));
        return new AuthenticatedPrincipal(
                auth.getUserId(),
                isAdmin ? orgID.toTeamServerUserID() : UserID.fromExternal(auth.getUserId()),
                orgID);
    }

    private boolean isAdminRequest(Collection<String> requestedScopes)
    {
        for (String s : requestedScopes) {
            if (Scope.requiresAdmin(Scope.fromName(s))) return true;
        }
        return false;
    }
}
