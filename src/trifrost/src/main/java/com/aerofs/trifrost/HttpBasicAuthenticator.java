package com.aerofs.trifrost;

import com.aerofs.trifrost.base.Constants;
import com.aerofs.trifrost.db.AuthTokens;
import com.aerofs.baseline.auth.AuthenticationResult;
import com.aerofs.baseline.auth.Authenticator;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.Set;

/**
 * Authenticator type for "Authorization: Basic " requests
 */
public class HttpBasicAuthenticator implements Authenticator {
    private static final Logger l = LoggerFactory.getLogger(HttpBasicAuthenticator.class);
    private static final Set<String> USER_ROLES = ImmutableSet.of(Constants.USER_ROLE);
    private final DBI dbi;

    public HttpBasicAuthenticator(DBI dbi) {
        this.dbi = dbi;
    }

    @Override
    public String getName() { return SecurityContext.BASIC_AUTH; }

    @Override
    public AuthenticationResult authenticate(MultivaluedMap<String, String> headers) {
        List<String> results = headers.get(HttpHeaders.AUTHORIZATION);

        if (results == null) return AuthenticationResult.UNSUPPORTED;
        if (results.size() != 1) return AuthenticationResult.FAILED;

        Preconditions.checkArgument(results.size() == 1);
        return decodeAndCheck(results.get(0));
    }

    private AuthenticationResult decodeAndCheck(String base64) {
        base64 = base64.replaceAll("[Bb]asic", "");
        base64 = base64.trim();

        byte[] decoded = BaseEncoding.base64().decode(base64);
        String authString = new String(decoded, Charsets.US_ASCII);

        String[] authComponents = authString.split(":");

        if (authComponents.length == 2) {
            return checkUser(authComponents[0], authComponents[1]);
        }
        return AuthenticationResult.FAILED;
    }

    private AuthenticationResult checkUser(String userId, String authToken) {
        boolean isValid = dbi.inTransaction((conn, status) -> conn.attach(AuthTokens.class).isValidForUser(authToken, userId)) > 0;
        AuthenticationResult result = isValid
                ? new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED, new BasicSecurityContext(userId, USER_ROLES))
                : AuthenticationResult.FAILED;
        l.info("HTTP BASIC u:{} r:{}", userId, result.getStatus().toString());
        return result;
    }
}
