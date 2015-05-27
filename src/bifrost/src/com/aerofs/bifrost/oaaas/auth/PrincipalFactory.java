/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.bifrost.oaaas.auth;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.bifrost.oaaas.auth.NonceChecker.AuthorizedClient;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.oauth.Scope;

import javax.inject.Inject;
import java.util.Collection;

public class PrincipalFactory
{
    @Inject
    private NonceChecker nonceChecker;

    public AuthenticatedPrincipal authenticate(
            String nonce, String devName, Collection<String> requestedScopes) throws Exception
    {
        AuthorizedClient auth = nonceChecker.authorizeAPIClient(nonce, devName);

        // is this a request for admin privilege; if so, throw if the caller is not allowed
        boolean isAdmin = isAdminRequest(requestedScopes);
        if (isAdmin && !auth.isOrgAdmin) {
            throw new ExNoPerm("User does not have admin privilege");
        }

        OrganizationID orgID = auth.orgId;
        return new AuthenticatedPrincipal(
                auth.userId.getString(),
                isAdmin ? orgID.toTeamServerUserID() : auth.userId,
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
