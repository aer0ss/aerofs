package com.aerofs.auth.server.delegated;

import com.aerofs.auth.server.AeroSecurityContext;
import com.aerofs.auth.server.Roles;
import com.aerofs.auth.server.SharedSecret;
import com.aerofs.baseline.auth.AuthenticationException;
import com.aerofs.baseline.auth.AuthenticationResult;
import com.aerofs.baseline.auth.Authenticator;
import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.google.common.net.HttpHeaders;

import javax.inject.Inject;
import javax.ws.rs.core.MultivaluedMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code Authenticator} implementation that constructs
 * {@code SecurityContext} instances for requests that use the
 * "Aero-Delegated-User-Device" authentication scheme.
 * <br>
 * This implementation throws {@link AuthenticationException} if
 * the authentication scheme is identified as "Aero-Delegated-User-Device"
 * and the payload has too few/too many parameters or the parameters
 * are invalid.
 * <br>
 * This implementation returns {@link AuthenticationResult#FAILED}
 * if the authentication scheme is identified as "Aero-Delegated-User-Device"
 * but the deployment secret encoded in the header does not match
 * the one used by this server.
 */
public final class AeroDelegatedUserDeviceAuthenticator implements Authenticator {

    private static final Pattern COMPILED_REGEX = Pattern.compile(AeroDelegatedUserDevice.AERO_DELEGATED_USER_DEVICE_HEADER_REGEX);

    private final SharedSecret deploymentSecret;

    /**
     * Constructor.
     *
     * @param deploymentSecret deployment secret shared between
     *                         all backend services in this AeroFS
     *                         installation
     */
    @Inject
    public AeroDelegatedUserDeviceAuthenticator(SharedSecret deploymentSecret) {
        this.deploymentSecret = deploymentSecret;
    }

    @Override
    public String getName() {
        return AeroDelegatedUserDevice.AUTHENTICATION_SCHEME;
    }

    @Override
    public AuthenticationResult authenticate(MultivaluedMap<String, String> headers) throws AuthenticationException {
        List<String> authHeaders = headers.get(HttpHeaders.AUTHORIZATION);

        // does it even have our auth header?
        if (authHeaders == null || authHeaders.size() == 0) {
            return AuthenticationResult.UNSUPPORTED;
        }

        // check that we only have one instance of that header
        if (authHeaders.size() > 1) {
            throw new AuthenticationException("multiple Authorization headers");
        }

        // it does - check if it's the "Aero-Delegated-User-Device" one
        String auth = authHeaders.get(0);
        if (!auth.startsWith(AeroDelegatedUserDevice.AUTHENTICATION_SCHEME)) {
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
        String device = matcher.group(4);

        // check if the username is encoded correctly
        String user;
        try {
            String encodedUser = matcher.group(3);
            user = new String(BaseEncoding.base64().decode(encodedUser), Charsets.UTF_8);
        } catch (IllegalArgumentException e) { // base64 decoding failed
            throw new AuthenticationException("invalid authentication scheme format");
        }

        // does their secret match ours?
        if (deploymentSecret.equalsString(reportedSecret)) {
            return new AuthenticationResult(AuthenticationResult.Status.SUCCEEDED, new AeroSecurityContext(new AeroDelegatedUserDevicePrincipal(service, user, device), Roles.USER, AeroDelegatedUserDevice.AUTHENTICATION_SCHEME));
        }

        return AuthenticationResult.FAILED;
    }
}
