package com.aerofs.auth.server.shared;

import com.aerofs.auth.server.AeroSecurityContext;
import com.aerofs.auth.server.Roles;
import com.aerofs.baseline.auth.AuthenticationException;
import com.aerofs.baseline.auth.AuthenticationResult;
import com.aerofs.baseline.auth.Authenticator;
import com.google.common.net.HttpHeaders;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code Authenticator} implementation that constructs
 * {@code SecurityContext} instances for requests that use the
 * "Aero-Service-Shared-Secret" authentication scheme.
 * <br>
 * This implementation throws {@link AuthenticationException} if
 * the authentication scheme is identified as "Aero-Service-Shared-Secret"
 * and the payload has too few/too many parameters or the parameters
 * are invalid.
 * <br>
 * This implementation returns {@link AuthenticationResult#FAILED}
 * if the authentication scheme is identified as "Aero-Service-Shared-Secret"
 * but the deployment secret encoded in the header does not match
 * the one used by this server.
 */
public final class AeroServiceSharedSecretAuthenticator implements Authenticator {

    private static final Pattern COMPILED_REGEX = Pattern.compile(AeroService.AERO_SERVICE_SHARED_SECRET_HEADER_REGEX);

    private final String deploymentSecret;

    /**
     * Constructor.
     *
     * @param deploymentSecret deployment secret shared between
     *                         all backend services in this AeroFS
     *                         installation
     */
    public AeroServiceSharedSecretAuthenticator(String deploymentSecret) {
        this.deploymentSecret = deploymentSecret;
    }

    @Override
    public String getName() {
        return AeroService.AUTHENTICATION_SCHEME;
    }

    @Override
    public AuthenticationResult authenticate(MultivaluedMap<String, String> headers) throws AuthenticationException {
        List<String> authHeaders = headers.get(HttpHeaders.AUTHORIZATION);

        // does it even have an auth header?
        if (authHeaders == null || authHeaders.size() == 0) {
            return AuthenticationResult.UNSUPPORTED;
        }

        // check that we only have one instance of that header
        if (authHeaders.size() > 1) {
            throw new AuthenticationException("multiple Authorization headers");
        }

        // it does - check if it's the "Aero-Service-Shared-Secret" one
        String auth = authHeaders.get(0);
        if (!auth.startsWith(AeroService.AUTHENTICATION_SCHEME)) {
            return AuthenticationResult.UNSUPPORTED;
        }

        // check if the authentication scheme format is correct
        Matcher matcher = COMPILED_REGEX.matcher(auth);
        if (!matcher.matches()) {
            throw new AuthenticationException("invalid authentication scheme format"); // explicitly don't specify what's broken
        }

        // it matches - parse it out, and get the other header values
        String service = matcher.group(1);
        String reportedSecret = matcher.group(2);

        // does their secret match ours?
        if (reportedSecret.equals(deploymentSecret)) {
            return new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED, new AeroSecurityContext(new AeroServicePrincipal(service), Roles.SERVICE, AeroService.AUTHENTICATION_SCHEME));
        }

        return AuthenticationResult.FAILED;
    }
}
