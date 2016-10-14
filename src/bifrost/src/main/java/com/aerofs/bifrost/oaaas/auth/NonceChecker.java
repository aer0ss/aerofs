package com.aerofs.bifrost.oaaas.auth;

import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.UserID;

public interface NonceChecker {
    AuthorizedClient authorizeAPIClient(String nonce, String devName) throws Exception;

    public static class AuthorizedClient {
        public final UserID userId;
        public final OrganizationID orgId;
        public final boolean isOrgAdmin;

        public AuthorizedClient(UserID u, OrganizationID o, boolean a)
        {
            userId = u;
            orgId = o;
            isOrgAdmin = a;
        }
    }
}
